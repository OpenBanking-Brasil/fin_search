"""
Arquivo local: áudio em Opus (compacto) e texto em ZIP por dia (DEFLATE).
Tudo o que é digitado e cada voz/transcrição fica com data e hora nos nomes e no texto.
Registo de teclado vem ligado por defeito — desative archive.keyboard_log em voice_config.json
se não quiser gravar teclas (ex.: palavras-passe).
"""

from __future__ import annotations

import subprocess
import threading
import time
import zipfile
from datetime import datetime
from pathlib import Path

import numpy as np

__all__ = [
    "ArchiveConfig",
    "KeyboardArchiver",
    "default_archive_dict",
    "merge_archive_config",
    "save_keyboard_flush",
    "save_ptt_audio_opus",
    "save_transcript_block",
]


def default_archive_dict() -> dict:
    return {
        "enabled": True,
        "root": "",
        "save_audio_opus": True,
        "opus_bitrate_kbps": 24,
        "save_transcripts": True,
        "keyboard_log": True,
        "keyboard_flush_seconds": 45,
    }


def merge_archive_config(cfg: dict) -> dict:
    out = default_archive_dict()
    raw = cfg.get("archive")
    if isinstance(raw, dict):
        out.update({k: v for k, v in raw.items() if k in out})
    return out


def _default_archive_root() -> Path:
    """Pasta predefinida: Desktop/LocalVoiceArchive (ou OneDrive Desktop se existir)."""
    home = Path.home()
    for desktop in (home / "Desktop", home / "OneDrive" / "Desktop"):
        if desktop.is_dir():
            return (desktop / "LocalVoiceArchive").resolve()
    return (home / "Documents" / "LocalVoiceArchive").resolve()


def _resolve_root(root: str) -> Path:
    if (root or "").strip():
        return Path(root).expanduser().resolve()
    return _default_archive_root()


class ArchiveConfig:
    def __init__(self, cfg: dict) -> None:
        self.raw = merge_archive_config(cfg)
        self.enabled: bool = bool(self.raw.get("enabled", True))
        self.root: Path = _resolve_root(str(self.raw.get("root", "")))
        self.save_audio_opus: bool = bool(self.raw.get("save_audio_opus", True))
        self.opus_bitrate_kbps: int = int(self.raw.get("opus_bitrate_kbps", 24))
        self.save_transcripts: bool = bool(self.raw.get("save_transcripts", True))
        self.keyboard_log: bool = bool(self.raw.get("keyboard_log", True))
        self.keyboard_flush_seconds: float = float(self.raw.get("keyboard_flush_seconds", 45))

    def audio_dir(self) -> Path:
        return self.root / "audio"

    def text_dir(self) -> Path:
        return self.root / "text"


def _ffmpeg_found() -> bool:
    try:
        r = subprocess.run(
            ["ffmpeg", "-version"],
            capture_output=True,
            timeout=5,
            creationflags=subprocess.CREATE_NO_WINDOW if __import__("sys").platform == "win32" else 0,
        )
        return r.returncode == 0
    except Exception:
        return False


def save_ptt_audio_opus(
    acfg: ArchiveConfig,
    audio: np.ndarray,
    sample_rate: int,
    stem_prefix: str = "voz",
) -> Path | None:
    if not acfg.enabled or not acfg.save_audio_opus:
        return None
    if audio is None or audio.size == 0:
        return None
    if not _ffmpeg_found():
        print("(Arquivo: ffmpeg nao encontrado no PATH; audio nao guardado.)")
        return None

    now = datetime.now()
    day = now.strftime("%Y-%m-%d")
    stamp = now.strftime("%Y-%m-%d_%H-%M-%S_%f")[:-3]
    folder = acfg.audio_dir() / day
    folder.mkdir(parents=True, exist_ok=True)
    stem = "".join(c for c in (stem_prefix or "voz") if c.isalnum() or c in "_-") or "voz"
    out_path = folder / f"{stem}_{stamp}.opus"

    audio = np.clip(audio.astype(np.float32), -1.0, 1.0)
    data = audio.tobytes()

    creationflags = subprocess.CREATE_NO_WINDOW if __import__("sys").platform == "win32" else 0
    try:
        proc = subprocess.Popen(
            [
                "ffmpeg",
                "-y",
                "-f",
                "f32le",
                "-ar",
                str(sample_rate),
                "-ac",
                "1",
                "-i",
                "pipe:0",
                "-c:a",
                "libopus",
                "-b:a",
                f"{acfg.opus_bitrate_kbps}k",
                "-v",
                "error",
                str(out_path),
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            creationflags=creationflags,
        )
        assert proc.stdin
        proc.stdin.write(data)
        proc.stdin.close()
        err = proc.stderr.read() if proc.stderr else b""
        rc = proc.wait(timeout=120)
        if rc != 0 or not out_path.is_file() or out_path.stat().st_size == 0:
            print(f"(Arquivo: falha ao guardar Opus: {err.decode('utf-8', errors='replace')[:200]})")
            try:
                out_path.unlink(missing_ok=True)
            except Exception:
                pass
            return None
        print(f"(Arquivo: audio -> {out_path})")
        return out_path
    except Exception as ex:
        print(f"(Arquivo: excecao ao guardar Opus: {ex})")
        return None


def _daily_zip_path(acfg: ArchiveConfig) -> Path:
    day = datetime.now().strftime("%Y-%m-%d")
    acfg.text_dir().mkdir(parents=True, exist_ok=True)
    return acfg.text_dir() / f"{day}.zip"


def _block_file_body(kind: str, body: str) -> str:
    now = datetime.now()
    d = now.strftime("%Y-%m-%d")
    h = now.strftime("%H:%M:%S")
    return f"data: {d}\nhora: {h}\ntipo: {kind}\n---\n{body}\n"


def save_transcript_block(acfg: ArchiveConfig, text: str, kind: str = "transcricao") -> None:
    if not acfg.enabled or not acfg.save_transcripts:
        return
    t = (text or "").strip()
    if not t:
        return
    zpath = _daily_zip_path(acfg)
    now = datetime.now()
    stamp = now.strftime("%Y-%m-%d_%H-%M-%S_%f")[:-3]
    name = f"{stamp}_{kind}.txt"
    content = _block_file_body(kind, t)
    try:
        with zipfile.ZipFile(zpath, "a", compression=zipfile.ZIP_DEFLATED) as zf:
            zf.writestr(name, content.encode("utf-8"))
        print(f"(Arquivo: texto -> {zpath.name} :: {name})")
    except Exception as ex:
        print(f"(Arquivo: falha ao guardar transcricao no zip: {ex})")


def save_keyboard_flush(acfg: ArchiveConfig, content: str) -> None:
    if not acfg.enabled or not acfg.keyboard_log:
        return
    t = (content or "").strip()
    if not t:
        return
    zpath = _daily_zip_path(acfg)
    now = datetime.now()
    stamp = now.strftime("%Y-%m-%d_%H-%M-%S_%f")[:-3]
    name = f"{stamp}_teclado.txt"
    content = _block_file_body("teclado", t)
    try:
        with zipfile.ZipFile(zpath, "a", compression=zipfile.ZIP_DEFLATED) as zf:
            zf.writestr(name, content.encode("utf-8"))
    except Exception:
        pass


def _event_to_token(name: str) -> str:
    n = (name or "").lower()
    if len(n) == 1 and n.isprintable():
        return n
    specials = {
        "space": " ",
        "enter": "\n",
        "tab": "\t",
        "decimal": ".",
    }
    if n in specials:
        return specials[n]
    return f"[{n}]"


class KeyboardArchiver:
    """Acumula texto a partir de eventos de teclado e descarrega para ZIP periodicamente."""

    def __init__(self, acfg: ArchiveConfig, shutdown: threading.Event) -> None:
        self.acfg = acfg
        self.shutdown = shutdown
        self._buf: list[str] = []
        self._lock = threading.Lock()
        self._last_flush = time.monotonic()
        self._thread: threading.Thread | None = None

    def _on_event(self, e) -> None:
        if self.shutdown.is_set():
            return
        et = (getattr(e, "event_type", "") or "").lower()
        if et != "down":
            return
        name = getattr(e, "name", "") or ""
        if name.lower() in ("backspace", "delete"):
            with self._lock:
                if self._buf and self._buf[-1] and not self._buf[-1].startswith("["):
                    self._buf[-1] = self._buf[-1][:-1]
            return
        tok = _event_to_token(name)
        with self._lock:
            self._buf.append(tok)

    def _loop(self) -> None:
        import keyboard as kb

        hook_h = kb.hook(self._on_event, suppress=False)
        try:
            while not self.shutdown.wait(1.0):
                now = time.monotonic()
                if now - self._last_flush >= self.acfg.keyboard_flush_seconds:
                    self._flush()
                    self._last_flush = now
        finally:
            try:
                self._flush()
            except Exception:
                pass
            try:
                kb.unhook(hook_h)
            except Exception:
                pass

    def _flush(self) -> None:
        with self._lock:
            if not self._buf:
                return
            text = "".join(self._buf)
            self._buf.clear()
        if len(text.strip()) < 1:
            return
        save_keyboard_flush(self.acfg, text)

    def start(self) -> None:
        if not self.acfg.enabled or not self.acfg.keyboard_log:
            return
        self._thread = threading.Thread(target=self._loop, name="keyboard-archiver", daemon=True)
        self._thread.start()
        print(
            "(Arquivo: registo de teclado ATIVO — tudo o que escrever sera guardado. "
            "Desative archive.keyboard_log em voice_config.json se usar palavras-passe.)"
        )

    def stop(self) -> None:
        self._flush()
