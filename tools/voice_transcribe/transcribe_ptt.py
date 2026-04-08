from __future__ import annotations

import json
import queue
import re
import sys
import ctypes
import threading
import time
from pathlib import Path
from typing import Callable

import keyboard
import numpy as np
import pyperclip
import pystray
import sounddevice as sd
from faster_whisper import WhisperModel
from PIL import Image, ImageDraw

from audio_input import print_input_devices, resolve_input_device
from dialogue_vad import DialogueSegmenter, make_dialogue_queue, merge_dialogue_config
from local_archive import ArchiveConfig, KeyboardArchiver, save_ptt_audio_opus, save_transcript_block


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

_SINGLE_INSTANCE_MUTEX = None

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
    "language": "auto",
    "paste_delay": 0.12,
    "desktop_button": False,
    "dialogue_choice_timeout_sec": 7.0,
    "dialogue_requires_confirmation": False,
}


class _POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]


class _RECT(ctypes.Structure):
    _fields_ = [("left", ctypes.c_long), ("top", ctypes.c_long), ("right", ctypes.c_long), ("bottom", ctypes.c_long)]


class _MONITORINFO(ctypes.Structure):
    _fields_ = [
        ("cbSize", ctypes.c_ulong),
        ("rcMonitor", _RECT),
        ("rcWork", _RECT),
        ("dwFlags", ctypes.c_ulong),
    ]


def get_cursor_monitor_workarea() -> tuple[int, int, int, int] | None:
    if sys.platform != "win32":
        return None
    user32 = ctypes.windll.user32
    pt = _POINT()
    if not user32.GetCursorPos(ctypes.byref(pt)):
        return None
    # MONITOR_DEFAULTTONEAREST = 2
    hmon = user32.MonitorFromPoint(pt, 2)
    if not hmon:
        return None
    mi = _MONITORINFO()
    mi.cbSize = ctypes.sizeof(_MONITORINFO)
    if not user32.GetMonitorInfoW(hmon, ctypes.byref(mi)):
        return None
    rc = mi.rcWork
    return int(rc.left), int(rc.top), int(rc.right), int(rc.bottom)


def script_dir() -> Path:
    return Path(__file__).resolve().parent


def _windows_single_instance_begin() -> bool:
    """
    Garante uma única instância no Windows (arranque automático + atalho não duplicam o processo).
    """
    global _SINGLE_INSTANCE_MUTEX
    if sys.platform != "win32":
        return True
    ERROR_ALREADY_EXISTS = 183
    kernel32 = ctypes.windll.kernel32
    name = "Local\\FinSearch_TranscricaoPTT"
    h = kernel32.CreateMutexW(None, False, name)
    if not h:
        return True
    if kernel32.GetLastError() == ERROR_ALREADY_EXISTS:
        kernel32.CloseHandle(h)
        return False
    _SINGLE_INSTANCE_MUTEX = h
    return True


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


def resolve_language_setting(raw_value: object) -> str | None:
    v = str(raw_value or "").strip().lower()
    if v in {"", "auto", "multilingual", "multi", "pt,en", "pt-br,en", "pt-pt,en"}:
        return None
    return v


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
        self.dialogue_segmenter: DialogueSegmenter | None = None
        self.dialogue_queue: queue.Queue | None = None
        self.dialogue_enabled: bool = False
        self.ui_event_cb: Callable[[str, str], None] | None = None
        self._last_dialogue_state: str = "idle"
        self._last_voice_event_ts: float = 0.0

    def callback(self, indata, frames, time_info, status):
        mono = indata[:, 0].astype(np.float32).copy()
        if self.recording:
            self.blocks.append(mono)
        if (
            self.dialogue_enabled
            and self.dialogue_segmenter is not None
            and self.dialogue_queue is not None
        ):
            try:
                seg = self.dialogue_segmenter.push(mono, ptt_active=self.recording)
                state_now = self.dialogue_segmenter.state
                if state_now == "recording" and self._last_dialogue_state != "recording":
                    now = time.monotonic()
                    if (now - self._last_voice_event_ts) > 0.45:
                        if self.ui_event_cb is not None:
                            self.ui_event_cb("voice_detected", "")
                            self.ui_event_cb("dialogue_started", "")
                        self._last_voice_event_ts = now
                self._last_dialogue_state = state_now
                if seg is not None:
                    self.dialogue_queue.put_nowait(seg)
            except queue.Full:
                print("(Dialogo: fila cheia; segmento descartado)")

    def start(self, device: int | None = None) -> None:
        kw: dict = {
            "samplerate": SAMPLE_RATE,
            "channels": 1,
            "dtype": "float32",
            "blocksize": BLOCK_SIZE,
            "callback": self.callback,
        }
        if device is not None:
            kw["device"] = device
        try:
            kw["latency"] = "low"
            self.stream = sd.InputStream(**kw)
        except Exception:
            kw.pop("latency", None)
            self.stream = sd.InputStream(**kw)
        self.stream.start()

    def stop(self):
        if self.stream is not None:
            self.stream.stop()
            self.stream.close()
            self.stream = None

    def begin(self):
        self.blocks = []
        self.recording = True
        if self.dialogue_segmenter is not None:
            self.dialogue_segmenter.reset()
            self._last_dialogue_state = "idle"

    def end(self) -> np.ndarray | None:
        self.recording = False
        if not self.blocks:
            return None
        return np.concatenate(self.blocks, axis=0)


class PTTController:
    def __init__(
        self,
        model: WhisperModel,
        cap: AudioCapture,
        cfg: dict,
        acfg: ArchiveConfig,
        event_cb: Callable[[str, str], None] | None = None,
    ) -> None:
        self.model = model
        self.cap = cap
        self.acfg = acfg
        self.language = resolve_language_setting(cfg.get("language", "auto"))
        self.paste_delay = float(cfg.get("paste_delay", 0.12))
        self.event_cb = event_cb
        self._pressed = False
        self._lock = threading.Lock()
        self._last_text_norm = ""
        self._last_text_ts = 0.0

    def is_pressed(self) -> bool:
        with self._lock:
            return self._pressed

    def press(self) -> None:
        with self._lock:
            if self._pressed:
                return
            self._pressed = True
            self.cap.begin()
        if self.event_cb is not None:
            self.event_cb("ptt_started", "")

    def release(self) -> None:
        with self._lock:
            if not self._pressed:
                return
            self._pressed = False
            audio = self.cap.end()
        if self.event_cb is not None:
            self.event_cb("ptt_stopped", "")

        if audio is None or audio.size == 0:
            return

        save_ptt_audio_opus(self.acfg, audio, SAMPLE_RATE)
        try:
            text = transcribe_whisper(self.model, audio, self.language)
        except Exception as ex:
            print(f"(PTT erro de transcricao: {ex})")
            return

        text = compress_repeats(text)
        if not text:
            return

        now = time.monotonic()
        norm = normalize_text(text)
        with self._lock:
            if norm and norm == self._last_text_norm and (now - self._last_text_ts) < DEDUP_WINDOW_SEC:
                print("(Duplicado ignorado)")
                return
            self._last_text_norm = norm
            self._last_text_ts = now

        save_transcript_block(self.acfg, text, "transcricao")
        paste_text(text, self.paste_delay)
        print(f" -> {text}")
        if self.event_cb is not None:
            self.event_cb("ptt_transcribed", text)


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


def transcribe_whisper(model: WhisperModel, audio: np.ndarray, language: str | None) -> str:
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


def run_dialogue_worker(
    model: WhisperModel,
    cfg: dict,
    shutdown: threading.Event,
    acfg: ArchiveConfig,
    audio_q: queue.Queue,
    event_cb: Callable[[str, str], None] | None = None,
    decision_q: queue.Queue[str] | None = None,
) -> None:
    """Transcreve segmentos detetados automaticamente (incluem pré-escuta)."""
    language = resolve_language_setting(cfg.get("language", "auto"))
    paste_delay = float(cfg.get("paste_delay", 0.12))
    dc = merge_dialogue_config(cfg)
    auto_paste = bool(dc.get("auto_paste", True))
    choice_timeout = float(cfg.get("dialogue_choice_timeout_sec", 7.0))
    require_confirmation = bool(cfg.get("dialogue_requires_confirmation", False))
    last_text_norm = ""
    last_text_ts = 0.0

    while not shutdown.is_set():
        try:
            audio = audio_q.get(timeout=0.45)
        except queue.Empty:
            continue
        if event_cb is not None:
            event_cb("voice_detected", "")
        if require_confirmation and decision_q is not None:
            # Remove decisões antigas para não aplicar cliques atrasados num novo diálogo.
            while True:
                try:
                    decision_q.get_nowait()
                except queue.Empty:
                    break
            if event_cb is not None:
                event_cb("dialogue_waiting_choice", "")
            decision = "transcribe"
            deadline = time.monotonic() + max(1.0, choice_timeout)
            while not shutdown.is_set():
                rem = deadline - time.monotonic()
                if rem <= 0:
                    break
                try:
                    decision = decision_q.get(timeout=min(0.25, rem))
                    break
                except queue.Empty:
                    continue
            if decision == "ignore":
                print("(Dialogo ignorado pelo botao.)")
                if event_cb is not None:
                    event_cb("dialogue_ignored", "")
                continue
        save_ptt_audio_opus(acfg, audio, SAMPLE_RATE, stem_prefix="dialogo")
        try:
            text = transcribe_whisper(model, audio, language)
        except Exception as ex:
            print(f"(Dialogo erro transcricao: {ex})")
            continue
        text = compress_repeats(text)
        if not text:
            continue
        now = time.monotonic()
        norm = normalize_text(text)
        if norm and norm == last_text_norm and (now - last_text_ts) < DEDUP_WINDOW_SEC:
            print("(Dialogo duplicado ignorado)")
            continue
        last_text_norm = norm
        last_text_ts = now
        save_transcript_block(acfg, text, "dialogo")
        if auto_paste:
            paste_text(text, paste_delay)
        print(f"(Dialogo) -> {text}")
        if event_cb is not None:
            event_cb("dialogue_transcribed", text)


def run_ptt_worker(
    controller: PTTController,
    cfg: dict,
    shutdown: threading.Event,
) -> None:
    hotkey = str(cfg.get("hotkey", "asterisk"))
    last_evt = {"key": "", "etype": "", "ts": 0.0}
    hook_handles: list = []
    fallback_hook_used = False
    poll_thread: threading.Thread | None = None

    def start_recording_if_needed() -> None:
        controller.press()

    def stop_recording_and_process() -> None:
        controller.release()

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


def run_desktop_button(
    shutdown: threading.Event,
    controller: PTTController,
    ui_events: queue.Queue,
    dialogue_decisions: queue.Queue[str] | None = None,
) -> None:
    try:
        import tkinter as tk
    except Exception as ex:
        print(f"(Botao desktop indisponivel: {ex})")
        return

    root = tk.Tk()
    root.title("Transcricao de voz")
    root.attributes("-topmost", False)
    root.resizable(False, False)
    try:
        root.attributes("-alpha", 0.94)
    except Exception:
        pass
    try:
        root.wm_attributes("-toolwindow", True)
    except Exception:
        pass

    status_var = tk.StringVar(value="Aguardando. Clique para iniciar.")
    button_var = tk.StringVar(value="Iniciar transcricao")
    dialogue_pending = {"value": False}

    frame = tk.Frame(root, padx=10, pady=10)
    frame.pack(fill="both", expand=True)

    label = tk.Label(frame, textvariable=status_var, width=42, anchor="w")
    label.pack(fill="x", pady=(0, 8))

    def refresh_button() -> None:
        if controller.is_pressed():
            button_var.set("Parar transcricao")
            status_var.set("Gravando... clique para parar.")
        else:
            button_var.set("Iniciar transcricao")

    def toggle_recording() -> None:
        if controller.is_pressed():
            controller.release()
        else:
            controller.press()
        refresh_button()

    button = tk.Button(frame, textvariable=button_var, width=28, command=toggle_recording)
    button.pack(fill="x")

    decision_frame = tk.Frame(frame, pady=6)
    decision_frame.pack(fill="x")

    def set_decision_buttons(enabled: bool) -> None:
        state = "normal" if enabled else "disabled"
        btn_dialogue_yes.configure(state=state)
        btn_dialogue_no.configure(state=state)

    def choose_dialogue_transcribe() -> None:
        if dialogue_decisions is None or not dialogue_pending["value"]:
            return
        try:
            dialogue_decisions.put_nowait("transcribe")
            dialogue_pending["value"] = False
            set_decision_buttons(False)
            status_var.set("Dialogo confirmado para transcricao.")
        except queue.Full:
            pass

    def choose_dialogue_ignore() -> None:
        if dialogue_decisions is None or not dialogue_pending["value"]:
            return
        try:
            dialogue_decisions.put_nowait("ignore")
            dialogue_pending["value"] = False
            set_decision_buttons(False)
            status_var.set("Dialogo ignorado.")
        except queue.Full:
            pass

    btn_dialogue_yes = tk.Button(
        decision_frame,
        text="Transcrever dialogo detectado",
        width=28,
        command=choose_dialogue_transcribe,
    )
    btn_dialogue_yes.pack(fill="x", pady=(2, 4))
    btn_dialogue_no = tk.Button(
        decision_frame,
        text="Ignorar dialogo detectado",
        width=28,
        command=choose_dialogue_ignore,
    )
    btn_dialogue_no.pack(fill="x")
    if dialogue_decisions is None:
        decision_frame.pack_forget()
    else:
        set_decision_buttons(False)

    def place_window() -> None:
        root.update_idletasks()
        width = max(root.winfo_width(), 360)
        height = max(root.winfo_height(), 96)
        work_area = get_cursor_monitor_workarea()
        if work_area is None:
            screen_w = root.winfo_screenwidth()
            screen_h = root.winfo_screenheight()
            x = max(20, screen_w - width - 36)
            y = max(20, screen_h - height - 78)
        else:
            left, top, right, bottom = work_area
            x = max(left + 12, right - width - 20)
            y = max(top + 12, bottom - height - 20)
        root.geometry(f"{width}x{height}+{x}+{y}")

    def pulse_window(strong: bool = False) -> None:
        try:
            place_window()
            root.deiconify()
            root.lift()
            root.attributes("-topmost", True)
            hold_ms = 1800 if strong else 700
            root.after(hold_ms, lambda: root.attributes("-topmost", False))
        except Exception:
            pass

    def poll_ui_events() -> None:
        if shutdown.is_set():
            try:
                root.destroy()
            except Exception:
                pass
            return

        while True:
            try:
                evt, _payload = ui_events.get_nowait()
            except queue.Empty:
                break

            if evt == "voice_detected":
                status_var.set("Voz detectada.")
                pulse_window()
            elif evt == "dialogue_started":
                status_var.set("Fala detectada.")
                pulse_window()
            elif evt == "dialogue_waiting_choice":
                status_var.set("Dialogo detectado. Escolha: transcrever ou ignorar.")
                dialogue_pending["value"] = True
                set_decision_buttons(True)
                pulse_window(strong=True)
            elif evt == "dialogue_transcribed":
                status_var.set("Dialogo transcrito.")
                dialogue_pending["value"] = False
                set_decision_buttons(False)
                pulse_window()
            elif evt == "dialogue_ignored":
                status_var.set("Dialogo ignorado.")
                dialogue_pending["value"] = False
                set_decision_buttons(False)
                pulse_window()
            elif evt == "ptt_transcribed":
                status_var.set("Transcricao concluida.")
                pulse_window()
            elif evt == "ptt_started":
                status_var.set("Gravando... clique para parar.")
            elif evt == "ptt_stopped":
                status_var.set("Gravacao parada.")

        refresh_button()
        root.after(220, poll_ui_events)

    def on_close() -> None:
        # Esconde a janela sem parar deteccao; volta ao detetar audio/dialogo.
        root.withdraw()

    root.protocol("WM_DELETE_WINDOW", on_close)
    place_window()
    refresh_button()
    root.after(220, poll_ui_events)
    root.mainloop()
    shutdown.set()


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
    if not _windows_single_instance_begin():
        sys.exit(0)

    cfg = load_config()

    model = WhisperModel(
        str(cfg.get("whisper_model", "tiny")),
        device=str(cfg.get("whisper_device", "cpu")),
        compute_type=str(cfg.get("whisper_compute_type", "int8")),
        cpu_threads=int(cfg.get("whisper_cpu_threads", 6)),
        num_workers=int(cfg.get("whisper_num_workers", 1)),
    )

    acfg = ArchiveConfig(cfg)
    if acfg.enabled:
        print(
            f"Arquivo local: {acfg.root} "
            f"(audio/YYYY-MM-DD/*.opus, texto/YYYY-MM-DD.zip)"
        )

    shutdown = threading.Event()
    cap = AudioCapture()
    ui_events: queue.Queue = queue.Queue(maxsize=64)
    require_dialogue_confirmation = bool(cfg.get("dialogue_requires_confirmation", False))
    dialogue_decisions: queue.Queue[str] | None = (
        queue.Queue(maxsize=4) if require_dialogue_confirmation else None
    )

    def emit_ui_event(kind: str, payload: str = "") -> None:
        try:
            ui_events.put_nowait((kind, payload))
        except queue.Full:
            pass
    cap.ui_event_cb = emit_ui_event

    dc = merge_dialogue_config(cfg)
    dialogue_thread: threading.Thread | None = None
    if dc.get("enabled"):
        cap.dialogue_queue = make_dialogue_queue()
        cap.dialogue_segmenter = DialogueSegmenter(cfg, SAMPLE_RATE)
        cap.dialogue_enabled = True

        def dialogue_entry() -> None:
            try:
                q = cap.dialogue_queue
                if q is not None:
                    run_dialogue_worker(
                        model,
                        cfg,
                        shutdown,
                        acfg,
                        q,
                        event_cb=emit_ui_event,
                        decision_q=dialogue_decisions,
                    )
            except Exception as ex:
                print(f"(Dialogo worker: {ex})")

        dialogue_thread = threading.Thread(target=dialogue_entry, name="dialogue", daemon=True)
        dialogue_thread.start()
        print(
            "(Dialogo automatico: deteccao de fala + pre-escuta de "
            f"{dc.get('pre_roll_seconds', 1.0)}s antes do inicio; PTT continua ativo.)"
        )
        if not require_dialogue_confirmation:
            print("(Dialogo: transcricao continua automatica, sem confirmacao manual.)")
        print(
            "(Mesmo microfone: a voz captada ao usar dictacao/transcricao noutra app "
            "(Notion, browser, Word, etc.) entra neste fluxo se o Windows estiver em "
            "modo partilhado e for o mesmo dispositivo — veja ARQUIVO_LOCAL.txt.)"
        )
    else:
        cap.dialogue_enabled = False

    if cfg.get("list_audio_devices"):
        print_input_devices()

    dev = resolve_input_device(cfg)
    try:
        cap.start(device=dev)
    except Exception as ex:
        print(f"(Audio: erro ao abrir microfone: {ex}. A tentar predefinido.)")
        print_input_devices()
        cap.start(device=None)

    hotkey = str(cfg.get("hotkey", "asterisk"))
    print(
        f"Microfone pronto. Segure '{hotkey}' para gravar e solte para transcrever/colar. "
        f"Whisper={cfg.get('whisper_model')} ({cfg.get('whisper_device')}/{cfg.get('whisper_compute_type')}) "
        f"idioma={'auto' if resolve_language_setting(cfg.get('language', 'auto')) is None else cfg.get('language')}"
    )
    print("Tradutor de botao direito removido.")

    ptt_thread: threading.Thread | None = None
    desktop_button_thread: threading.Thread | None = None
    kbd_arch: KeyboardArchiver | None = None
    ptt_controller = PTTController(model, cap, cfg, acfg, event_cb=emit_ui_event)

    def start_ptt_thread() -> None:
        nonlocal ptt_thread

        def ptt_entry() -> None:
            try:
                run_ptt_worker(ptt_controller, cfg, shutdown)
            except Exception as ex:
                print(f"(Worker PTT falhou: {ex})")

        ptt_thread = threading.Thread(target=ptt_entry, name="ptt-worker", daemon=True)
        ptt_thread.start()

    kbd_arch = KeyboardArchiver(acfg, shutdown)
    try:
        kbd_arch.start()
    except Exception as ex:
        print(f"(Arquivo: teclado nao iniciado: {ex})")

    start_ptt_thread()
    if bool(cfg.get("desktop_button", True)):
        desktop_button_thread = threading.Thread(
            target=run_desktop_button,
            args=(shutdown, ptt_controller, ui_events, dialogue_decisions),
            name="desktop-button",
            daemon=True,
        )
        desktop_button_thread.start()
        print("(Botao desktop ativo: usar para iniciar/parar transcricao.)")

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
        if kbd_arch is not None:
            try:
                kbd_arch.stop()
            except Exception:
                pass
        if ptt_thread is not None and ptt_thread.is_alive():
            ptt_thread.join(timeout=15.0)
        if dialogue_thread is not None and dialogue_thread.is_alive():
            dialogue_thread.join(timeout=20.0)
        if desktop_button_thread is not None and desktop_button_thread.is_alive():
            desktop_button_thread.join(timeout=2.0)
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
