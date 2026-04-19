"""
Deteção contínua de diálogo: buffer de pré-escuta (áudio antes do início da fala)
e VAD por energia (RMS) com limiar adaptativo.
"""

from __future__ import annotations

import queue
import numpy as np


def merge_dialogue_config(cfg: dict) -> dict:
    defaults = {
        "enabled": False,
        "pre_roll_seconds": 1.0,
        "silence_to_end_seconds": 0.55,
        "min_speech_seconds": 0.25,
        "cooldown_seconds": 0.8,
        "rms_speech_start": 5.0,
        "rms_speech_end": 2.2,
        "noise_floor_leak": 0.05,
        "auto_paste": True,
    }
    raw = cfg.get("dialogue_detection")
    if isinstance(raw, dict):
        defaults.update({k: v for k, v in raw.items() if k in defaults})
    return defaults


class RingBuffer:
    """Buffer circular para os últimos `capacity` samples (float32 mono)."""

    __slots__ = ("capacity", "data", "pos", "size")

    def __init__(self, capacity: int) -> None:
        self.capacity = max(1, int(capacity))
        self.data = np.zeros(self.capacity, dtype=np.float32)
        self.pos = 0
        self.size = 0

    def clear(self) -> None:
        self.pos = 0
        self.size = 0

    def extend(self, chunk: np.ndarray) -> None:
        c = np.asarray(chunk, dtype=np.float32).ravel()
        for i in range(len(c)):
            self.data[self.pos] = c[i]
            self.pos = (self.pos + 1) % self.capacity
            self.size = min(self.size + 1, self.capacity)

    def snapshot(self) -> np.ndarray:
        """Conteúdo linear do mais antigo ao mais recente."""
        if self.size == 0:
            return np.array([], dtype=np.float32)
        if self.size < self.capacity:
            return self.data[: self.size].copy()
        return np.concatenate((self.data[self.pos :], self.data[: self.pos]))


class DialogueSegmenter:
    """
    Máquina de estados: à espera de fala -> a gravar segmento até silêncio.
    Inclui sempre o pré-roll no início do segmento emitido.
    """

    def __init__(self, full_cfg: dict, sample_rate: int) -> None:
        m = merge_dialogue_config(full_cfg)
        self.sample_rate = sample_rate
        self.pre_roll_samples = max(1, int(float(m["pre_roll_seconds"]) * sample_rate))
        self.silence_samples = max(1, int(float(m["silence_to_end_seconds"]) * sample_rate))
        self.min_speech_samples = max(1, int(float(m["min_speech_seconds"]) * sample_rate))
        self.cooldown_samples = max(0, int(float(m["cooldown_seconds"]) * sample_rate))
        self.rms_start = float(m["rms_speech_start"])
        self.rms_end = float(m["rms_speech_end"])
        self.leak = float(m["noise_floor_leak"])

        self.ring = RingBuffer(self.pre_roll_samples)
        self.noise_floor = 0.0008
        self.state: str = "idle"  # idle | recording
        self.segment_chunks: list[np.ndarray] = []
        self.segment_speech_samples = 0
        self.silence_run_samples = 0
        self.speech_run_samples = 0
        self.cooldown_left = 0

    def reset(self) -> None:
        """Chamar ao iniciar PTT para não misturar com diálogo automático."""
        self.ring.clear()
        self.state = "idle"
        self.segment_chunks.clear()
        self.segment_speech_samples = 0
        self.silence_run_samples = 0
        self.speech_run_samples = 0

    def _rms(self, chunk: np.ndarray) -> float:
        if chunk.size == 0:
            return 0.0
        return float(np.sqrt(np.mean(chunk * chunk) + 1e-18))

    def push(self, chunk: np.ndarray, ptt_active: bool) -> np.ndarray | None:
        """
        Recebe um bloco mono float32. Se ptt_active, limpa e não deteta.
        Retorna áudio completo do segmento quando a frase termina (silêncio), senão None.
        """
        if ptt_active:
            self.reset()
            return None

        c = np.asarray(chunk, dtype=np.float32).ravel()
        if c.size == 0:
            return None

        if self.cooldown_left > 0:
            self.cooldown_left -= c.size
            self.ring.extend(c)
            return None

        rms = self._rms(c)
        self.noise_floor = (1.0 - self.leak) * self.noise_floor + self.leak * rms
        floor = max(self.noise_floor, 1e-6)
        is_speech = rms > floor * self.rms_start
        is_quiet = rms < floor * self.rms_end

        self.ring.extend(c)

        if self.state == "idle":
            if is_speech:
                self.speech_run_samples += c.size
            else:
                self.speech_run_samples = 0

            if self.speech_run_samples >= self.min_speech_samples:
                self.state = "recording"
                pre = self.ring.snapshot()
                self.segment_chunks = []
                if pre.size > 0:
                    self.segment_chunks.append(pre)
                else:
                    self.segment_chunks.append(c.copy())
                self.segment_speech_samples = sum(x.size for x in self.segment_chunks)
                self.silence_run_samples = 0
                self.speech_run_samples = 0
            return None

        # recording
        self.segment_chunks.append(c.copy())
        self.segment_speech_samples += c.size

        if is_quiet:
            self.silence_run_samples += c.size
        else:
            self.silence_run_samples = 0

        if self.segment_speech_samples < self.min_speech_samples:
            return None

        if self.silence_run_samples >= self.silence_samples:
            out = np.concatenate(self.segment_chunks, axis=0)
            self.segment_chunks.clear()
            self.state = "idle"
            self.silence_run_samples = 0
            self.speech_run_samples = 0
            self.cooldown_left = self.cooldown_samples
            if out.size < self.min_speech_samples:
                return None
            return out

        return None


def make_dialogue_queue(maxsize: int = 6) -> queue.Queue[np.ndarray]:
    return queue.Queue(maxsize=maxsize)
