from __future__ import annotations

import json
import re
import threading
import time
from pathlib import Path

import keyboard
import numpy as np
import pyperclip
import pystray
import sounddevice as sd
from deep_translator import GoogleTranslator
from faster_whisper import WhisperModel
from PIL import Image, ImageDraw

SAMPLE_RATE = 16000
BLOCK_SIZE = 1024
DEDUP_WINDOW_SEC = 8.0
NUMPAD_ASTERISK_SCAN_CODE = 55
CLIPBOARD_LOCK = threading.Lock()
DEFAULT_CONFIG = {
    "hotkey": "asterisk",
    "whisper_model": "tiny",
    "whisper_device": "cpu",
    "whisper_compute_type": "int8",
    "whisper_cpu_threads": 6,
    "whisper_num_workers": 1,
    "language": "pt",
    "paste_delay": 0.12,
    "translate_source": "auto",
    "translate_target": "pt",
    "translate_copy_delay": 0.18,
}


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def load_config() -> dict:
    cfg = dict(DEFAULT_CONFIG)
    p = script_dir() / "voice_config.json"
    if p.is_file():
        try:
            raw = json.loads(p.read_text(encoding="utf-8"))
            if isinstance(raw, dict):
                cfg.update(raw)
        except Exception:
            pass
    return cfg


def normalize_text(s: str) -> str:
    t = s.casefold()
    t = re.sub(r"[^\w\s]", " ", t, flags=re.UNICODE)
    t = t.replace("_", " ")
    t = re.sub(r"\s+", " ", t).strip()
    return t


def compress_repeats(text: str) -> str:
    words = text.split()
    if not words:
        return text.strip()
    out = []
    last = None
    for w in words:
        n = normalize_text(w)
        if n and n == last:
            continue
        out.append(w)
        last = n
    return " ".join(out).strip()


def is_hotkey_event(name: str, hotkey: str, scan_code: int | None = None) -> bool:
    n = (name or "").lower().strip()
    hk = (hotkey or "").lower().strip()
    if hk == "asterisk":
        if scan_code == NUMPAD_ASTERISK_SCAN_CODE:
            return True
        return n in {"*", "multiply", "numpad *", "numpad multiply", "num *", "num multiply"}
    return n == hk


class AudioCapture:
    def __init__(self) -> None:
        self.blocks: list[np.ndarray] = []
        self.recording = False
        self.stream: sd.InputStream | None = None

    def callback(self, indata, frames, time_info, status):
        if self.recording:
            self.blocks.append(indata[:, 0].astype(np.float32).copy())

    def start(self):
        self.stream = sd.InputStream(
            samplerate=SAMPLE_RATE,
            channels=1,
            dtype="float32",
            blocksize=BLOCK_SIZE,
            callback=self.callback,
        )
        self.stream.start()

    def stop(self):
        if self.stream is not None:
            self.stream.stop()
            self.stream.close()
            self.stream = None

    def begin(self):
        self.blocks = []
        self.recording = True

    def end(self) -> np.ndarray | None:
        self.recording = False
        if not self.blocks:
            return None
        return np.concatenate(self.blocks, axis=0)


def create_tray_image() -> Image.Image:
    w, h = 64, 64
    img = Image.new("RGB", (w, h), (45, 52, 65))
    d = ImageDraw.Draw(img)
    d.ellipse((18, 10, 46, 38), fill=(180, 190, 210))
    d.rounded_rectangle((26, 38, 38, 52), radius=4, fill=(180, 190, 210))
    return img


def translate_text(text: str, source: str, target: str) -> str:
    t = (text or "").strip()
    if not t:
        return ""
    src = (source or "auto").strip().lower()
    tgt = (target or "pt").strip().lower()
    tr = GoogleTranslator(source=src, target=tgt)
    max_len = 4500
    if len(t) <= max_len:
        return tr.translate(t)
    parts: list[str] = []
    for i in range(0, len(t), max_len):
        parts.append(tr.translate(t[i : i + max_len]))
    return "".join(parts)


def paste_text(text: str, delay_sec: float) -> None:
    if not text:
        return
    with CLIPBOARD_LOCK:
        old = None
        try:
            old = pyperclip.paste()
        except Exception:
            old = None
        pyperclip.copy(text)
        time.sleep(delay_sec)
        keyboard.send("ctrl+v")
        if old is not None:
            time.sleep(0.04)
            try:
                pyperclip.copy(old)
            except Exception:
                pass


def translate_selection(cfg: dict) -> None:
    copy_delay = float(cfg.get("translate_copy_delay", 0.18))
    source = str(cfg.get("translate_source", "auto"))
    target = str(cfg.get("translate_target", "pt"))
    paste_delay = float(cfg.get("paste_delay", 0.12))
    with CLIPBOARD_LOCK:
        keyboard.send("ctrl+c")
        time.sleep(copy_delay)
        try:
            raw = pyperclip.paste()
        except Exception:
            raw = ""
    text = (raw or "").strip()
    if not text:
        print("(Tradução: nada selecionado ou cópia vazia)")
        return
    try:
        out = translate_text(text, source, target)
    except Exception as ex:
        print(f"(Tradução falhou: {ex})")
        return
    if not out.strip():
        print("(Tradução: resultado vazio)")
        return
    paste_text(out, paste_delay)
    preview = out[:200] + ("..." if len(out) > 200 else "")
    print(f" -> [traduzido] {preview}")


def transcribe_whisper(model: WhisperModel, audio: np.ndarray, language: str) -> str:
    segments, _info = model.transcribe(
        audio,
        language=language,
        beam_size=1,
        best_of=1,
        temperature=0.0,
        condition_on_previous_text=False,
        vad_filter=True,
        without_timestamps=True,
    )
    parts = []
    for seg in segments:
        t = (seg.text or "").strip()
        if t:
            parts.append(t)
    return " ".join(parts).strip()


def run_ptt_worker(
    model: WhisperModel,
    cap: AudioCapture,
    cfg: dict,
    shutdown: threading.Event,
) -> None:
    hotkey = str(cfg.get("hotkey", "asterisk"))
    language = str(cfg.get("language", "pt"))
    paste_delay = float(cfg.get("paste_delay", 0.12))

    state = {"pressed": False}
    last_text_norm = ""
    last_text_ts = 0.0
    last_evt = {"key": "", "etype": "", "ts": 0.0}
    hook_handles: list = []

    def on_event(e):
        nonlocal last_text_norm, last_text_ts
        if shutdown.is_set():
            return
        name = (getattr(e, "name", "") or "").lower()
        et = (getattr(e, "event_type", "") or "").lower()
        scan_code = getattr(e, "scan_code", None)
        now_evt = time.monotonic()
        if (
            name == last_evt["key"]
            and et == last_evt["etype"]
            and (now_evt - float(last_evt["ts"])) < 0.01
        ):
            return
        last_evt["key"] = name
        last_evt["etype"] = et
        last_evt["ts"] = now_evt

        if not is_hotkey_event(name, hotkey, scan_code=scan_code):
            return
        if et == "down":
            if state["pressed"]:
                return
            state["pressed"] = True
            cap.begin()
        elif et == "up":
            if not state["pressed"]:
                return
            state["pressed"] = False
            audio = cap.end()
            if audio is None or audio.size == 0:
                return
            text = transcribe_whisper(model, audio, language)
            text = compress_repeats(text)
            if not text:
                return
            now = time.monotonic()
            norm = normalize_text(text)
            if norm and norm == last_text_norm and (now - last_text_ts) < DEDUP_WINDOW_SEC:
                print("(Duplicado ignorado)")
                return
            last_text_norm = norm
            last_text_ts = now
            paste_text(text, paste_delay)
            print(f" -> {text}")

    if hotkey.lower().strip() == "asterisk":
        for key_name in (NUMPAD_ASTERISK_SCAN_CODE, "multiply", "numpad *", "*"):
            try:
                h = keyboard.hook_key(key_name, on_event, suppress=True)
                hook_handles.append(h)
            except Exception:
                pass
        if not hook_handles:
            keyboard.hook(on_event)
    else:
        keyboard.hook(on_event)
    try:
        while not shutdown.wait(3600.0):
            pass
    finally:
        if hook_handles:
            for h in hook_handles:
                try:
                    keyboard.unhook(h)
                except Exception:
                    pass
        else:
            try:
                keyboard.unhook_all()
            except Exception:
                pass
        cap.stop()


def run_tray(cfg: dict, shutdown: threading.Event, ptt_thread: threading.Thread) -> None:
    image = create_tray_image()

    def on_translate(_icon, _item):
        translate_selection(cfg)

    def on_quit(icon, _item):
        shutdown.set()
        icon.stop()

    menu = pystray.Menu(
        pystray.MenuItem("Traduzir seleção", on_translate),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Sair", on_quit),
    )
    icon = pystray.Icon(
        "transcribe_ptt",
        image,
        "Transcrição PTT — clique direito para o menu",
        menu,
    )
    icon.run()
    shutdown.set()
    ptt_thread.join(timeout=15.0)


def main() -> None:
    cfg = load_config()

    model = WhisperModel(
        str(cfg.get("whisper_model", "tiny")),
        device=str(cfg.get("whisper_device", "cpu")),
        compute_type=str(cfg.get("whisper_compute_type", "int8")),
        cpu_threads=int(cfg.get("whisper_cpu_threads", 6)),
        num_workers=int(cfg.get("whisper_num_workers", 1)),
    )

    cap = AudioCapture()
    cap.start()

    hotkey = str(cfg.get("hotkey", "asterisk"))
    print(
        f"Microfone pronto. Segura '{hotkey}' para gravar e solta para transcrever/colar. "
        f"Whisper={cfg.get('whisper_model')} ({cfg.get('whisper_device')}/{cfg.get('whisper_compute_type')})"
    )
    print("Ícone na bandeja: clique direito → «Traduzir seleção» (texto já selecionado na app ativa).")

    shutdown = threading.Event()
    ptt_thread = threading.Thread(
        target=run_ptt_worker,
        args=(model, cap, cfg, shutdown),
        name="ptt-worker",
        daemon=True,
    )
    ptt_thread.start()
    try:
        run_tray(cfg, shutdown, ptt_thread)
    except KeyboardInterrupt:
        shutdown.set()
    finally:
        shutdown.set()
        if ptt_thread.is_alive():
            ptt_thread.join(timeout=15.0)


if __name__ == "__main__":
    main()
