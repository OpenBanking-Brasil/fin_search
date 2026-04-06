"""
Monitor da área de transferência (Windows): grava cada cópia de texto com data/hora
e a “fonte” aproximada (título da janela em foco + nome do processo no momento da cópia).

Limitação: o SO não guarda URL/arquivo de origem na clipboard; o foco pode mudar
antes de lermos — é a melhor estimativa sem drivers ou hooks de baixo nível.
"""

from __future__ import annotations

import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

import ctypes
from ctypes import wintypes

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

CF_UNICODETEXT = 13
PROCESS_QUERY_LIMITED_INFORMATION = 0x1000

# GetClipboardSequenceNumber: incrementa quando a clipboard muda (Vista+)
user32.GetClipboardSequenceNumber.argtypes = []
user32.GetClipboardSequenceNumber.restype = wintypes.DWORD


def _get_foreground_window_title() -> str:
    hwnd = user32.GetForegroundWindow()
    if not hwnd:
        return ""
    length = user32.GetWindowTextLengthW(hwnd)
    if length <= 0:
        return ""
    buf = ctypes.create_unicode_buffer(length + 1)
    user32.GetWindowTextW(hwnd, buf, length + 1)
    return buf.value or ""


def _get_foreground_pid() -> int:
    hwnd = user32.GetForegroundWindow()
    if not hwnd:
        return 0
    pid = wintypes.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    return int(pid.value)


def _get_process_exe_basename(pid: int) -> str:
    if pid <= 0:
        return ""
    h = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
    if not h:
        return f"pid:{pid}"
    try:
        buf = ctypes.create_unicode_buffer(32768)
        size = wintypes.DWORD(len(buf))
        # Kernel32 QueryFullProcessImageNameW
        q = kernel32.QueryFullProcessImageNameW
        q.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
        q.restype = wintypes.BOOL
        if q(h, 0, buf, ctypes.byref(size)):
            return Path(buf.value).name
    finally:
        kernel32.CloseHandle(h)
    return f"pid:{pid}"


def _get_clipboard_text() -> str | None:
    if not user32.OpenClipboard(None):
        return None
    try:
        handle = user32.GetClipboardData(CF_UNICODETEXT)
        if not handle:
            return None
        ptr = kernel32.GlobalLock(handle)
        if not ptr:
            return None
        try:
            return ctypes.wstring_at(ptr)
        finally:
            kernel32.GlobalUnlock(handle)
    finally:
        user32.CloseClipboard()


def _load_config(base: Path) -> dict:
    p = base / "clipboard_config.json"
    ex = base / "clipboard_config.example.json"
    for candidate in (p, ex):
        if candidate.is_file():
            try:
                return json.loads(candidate.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                pass
    return {}


def _resolve_root(base: Path, cfg: dict) -> Path:
    raw = (cfg.get("root") or "").strip()
    if raw:
        return Path(os.path.expandvars(raw)).expanduser().resolve()
    return (base / "data").resolve()


def _write_entry(
    root: Path,
    text: str,
    source_window: str,
    source_process: str,
    source_pid: int,
    max_chars: int,
) -> Path:
    now = datetime.now()
    day = now.strftime("%Y-%m-%d")
    hour = now.strftime("%H")
    # pasta: data / dia / hora
    dir_path = root / day / hour
    dir_path.mkdir(parents=True, exist_ok=True)

    fname = now.strftime("%M-%S_%f") + ".txt"
    path = dir_path / fname

    body = text if len(text) <= max_chars else text[:max_chars] + "\n\n[… truncado — aumente max_chars em clipboard_config.json …]"
    header = (
        f"timestamp_iso: {now.isoformat(timespec='milliseconds')}\n"
        f"source_window: {source_window.replace(chr(10), ' ')}\n"
        f"source_process: {source_process}\n"
        f"source_pid: {source_pid}\n"
        f"---\n"
    )
    path.write_text(header + body, encoding="utf-8")
    return path


def main() -> int:
    base = Path(__file__).resolve().parent
    cfg = _load_config(base)
    root = _resolve_root(base, cfg)
    poll_ms = int(cfg.get("poll_interval_ms", 400))
    max_chars = int(cfg.get("max_text_chars", 500_000))
    skip_duplicate = bool(cfg.get("skip_duplicate_text", True))

    last_seq = user32.GetClipboardSequenceNumber()
    last_text: str | None = None

    print(f"clipboard_archive: gravando em {root}", flush=True)
    print("Ctrl+C para parar.", flush=True)

    try:
        while True:
            time.sleep(poll_ms / 1000.0)
            seq = user32.GetClipboardSequenceNumber()
            if seq == last_seq:
                continue
            last_seq = seq

            text = _get_clipboard_text()
            if text is None:
                continue
            if not text.strip():
                continue
            if skip_duplicate and last_text == text:
                continue
            last_text = text

            # Fonte: janela/processo em foco AGORA (pode diferir ligeiramente do instante exato do Ctrl+C)
            wtitle = _get_foreground_window_title()
            pid = _get_foreground_pid()
            proc = _get_process_exe_basename(pid)

            path = _write_entry(root, text, wtitle, proc, pid, max_chars)
            print(f"gravado: {path}", flush=True)
    except KeyboardInterrupt:
        print("\nEncerrado.", flush=True)
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
