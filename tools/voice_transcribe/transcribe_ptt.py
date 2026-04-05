from __future__ import annotations

import json
import re
import sys
import threading
import time
from pathlib import Path

import keyboard
import numpy as np
import pyperclip
import pystray
import sounddevice as sd
from faster_whisper import WhisperModel
from PIL import Image, ImageDraw


def _configure_stdio_utf8() -> None:
    """Evita UnicodeEncodeError em consoles Windows."""
    if sys.platform != "win32":
        return
    for stream in (sys.stdout, sys.stderr):
        if stream is None:
            continue
        reconf = getattr(stream, "reconfigure", None)
        if callable(reconf):
            try:
                reconf(encoding="utf-8", errors="replace")
            except Exception:
                pass


_configure_stdio_utf8()

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
    out: list[str] = []
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
        return n in {"*", "multiply", "numpad *", "numpad multiply", "num *", "num multiply", "asterisk"}
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
    parts: list[str] = []
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
    fallback_hook_used = False
    poll_thread: threading.Thread | None = None

    def start_recording_if_needed() -> None:
        if state["pressed"]:
            return
        state["pressed"] = True
        cap.begin()

    def stop_recording_and_process() -> None:
        nonlocal last_text_norm, last_text_ts
        if not state["pressed"]:
            return
        state["pressed"] = False
        audio = cap.end()
        if audio is None or audio.size == 0:
            return
        try:
            text = transcribe_whisper(model, audio, language)
        except Exception as ex:
            print(f"(PTT erro de transcricao: {ex})")
            return
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

    def on_event(e):
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
            start_recording_if_needed()
        elif et == "up":
            stop_recording_and_process()

    def run_asterisk_poller() -> None:
        """Fallback: detecta '*' mesmo quando hooks falham em algumas apps/layouts."""
        prev = False
        while not shutdown.is_set():
            pressed = False
            try:
                pressed = bool(
                    keyboard.is_pressed(NUMPAD_ASTERISK_SCAN_CODE)
                    or keyboard.is_pressed("asterisk")
                    or keyboard.is_pressed("*")
                )
            except Exception:
                pressed = False
            if pressed and not prev:
                start_recording_if_needed()
            elif prev and not pressed:
                stop_recording_and_process()
            prev = pressed
            time.sleep(0.02)

    if hotkey.lower().strip() == "asterisk":
        for key_name in (NUMPAD_ASTERISK_SCAN_CODE, "multiply", "numpad *", "*", "asterisk"):
            try:
                h = keyboard.hook_key(key_name, on_event, suppress=True)
                hook_handles.append(h)
            except Exception:
                pass
        if not hook_handles:
            print("(Aviso: hook dedicado para '*' falhou; usando fallback global.)")
            keyboard.hook(on_event)
            fallback_hook_used = True
        else:
            print(f"Hotkey '*' ativo ({len(hook_handles)} hooks).")
        poll_thread = threading.Thread(target=run_asterisk_poller, name="asterisk-poller", daemon=True)
        poll_thread.start()
    else:
        keyboard.hook(on_event)
        fallback_hook_used = True

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
        elif fallback_hook_used:
            try:
                keyboard.unhook_all()
            except Exception:
                pass
        if poll_thread is not None and poll_thread.is_alive():
            poll_thread.join(timeout=1.0)


def run_tray(shutdown: threading.Event) -> None:
    image = create_tray_image()

    def on_quit(icon, _item):
        shutdown.set()
        icon.stop()

    menu = pystray.Menu(pystray.MenuItem("Sair", on_quit))
    icon = pystray.Icon(
        "transcribe_ptt",
        image,
        "Transcricao PTT (asterisco) - clique para sair",
        menu,
    )
    icon.run()
    shutdown.set()


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
        f"Microfone pronto. Segure '{hotkey}' para gravar e solte para transcrever/colar. "
        f"Whisper={cfg.get('whisper_model')} ({cfg.get('whisper_device')}/{cfg.get('whisper_compute_type')})"
    )
    print("Tradutor de botao direito removido.")

    shutdown = threading.Event()
    ptt_thread: threading.Thread | None = None

    def start_ptt_thread() -> None:
        nonlocal ptt_thread

        def ptt_entry() -> None:
            try:
                run_ptt_worker(model, cap, cfg, shutdown)
            except Exception as ex:
                print(f"(Worker PTT falhou: {ex})")

        ptt_thread = threading.Thread(target=ptt_entry, name="ptt-worker", daemon=True)
        ptt_thread.start()

    start_ptt_thread()
    tray_thread = threading.Thread(target=run_tray, args=(shutdown,), name="tray", daemon=True)
    tray_thread.start()

    try:
        while not shutdown.wait(2.0):
            if ptt_thread is None or not ptt_thread.is_alive():
                print("(Watchdog) reiniciando worker PTT.")
                start_ptt_thread()
    except KeyboardInterrupt:
        shutdown.set()
    finally:
        shutdown.set()
        if ptt_thread is not None and ptt_thread.is_alive():
            ptt_thread.join(timeout=15.0)
        if tray_thread.is_alive():
            tray_thread.join(timeout=2.0)
        try:
            cap.stop()
        except Exception:
            pass


if __name__ == "__main__":
    restart_delay = 5.0
    while True:
        try:
            main()
            break
        except KeyboardInterrupt:
            break
        except Exception as ex:
            print(f"(Falha inesperada: {ex}. Reiniciando em {restart_delay:.0f}s)")
            time.sleep(restart_delay)
