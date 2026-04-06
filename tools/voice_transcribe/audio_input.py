"""
Resolução do dispositivo de entrada (microfone) e listagem para depuração.
No Windows, o modo partilhado (predefinido) permite que várias apps usem o mesmo microfone.
"""

from __future__ import annotations

import sys
from typing import Any

import sounddevice as sd


def list_input_devices() -> list[tuple[int, str]]:
    out: list[tuple[int, str]] = []
    for i, d in enumerate(sd.query_devices()):
        if int(d.get("max_input_channels", 0) or 0) < 1:
            continue
        name = str(d.get("name", "") or "")
        out.append((i, name))
    return out


def _default_input_index() -> int | None:
    try:
        di = sd.default.device
        if isinstance(di, (list, tuple)) and len(di) >= 1:
            return int(di[0])
        return int(di)
    except Exception:
        return None


def print_input_devices() -> None:
    default_in = _default_input_index()
    print("Dispositivos de entrada (microfone) disponiveis:")
    for idx, name in list_input_devices():
        mark = " (predefinido Windows)" if default_in is not None and idx == default_in else ""
        print(f"  [{idx}] {name}{mark}")


def resolve_input_device(cfg: dict[str, Any]) -> int | None:
    """
    cfg.input_device:
      - omitido / null / ""  -> None (sounddevice usa o predefinido)
      - numero inteiro       -> indice PortAudio
      - string numerica      -> indice
      - string texto         -> primeiro dispositivo de entrada cujo nome contem a substring (sem acentos case-insensitive)
    """
    raw = cfg.get("input_device")
    if raw is None or raw == "":
        return None
    if isinstance(raw, bool):
        return None
    if isinstance(raw, int):
        return _validate_device_index(raw)
    if isinstance(raw, float):
        return _validate_device_index(int(raw))
    if isinstance(raw, str):
        s = raw.strip()
        if not s:
            return None
        if s.isdigit():
            return _validate_device_index(int(s))
        needle = s.casefold()
        matches: list[tuple[int, str]] = []
        for idx, name in list_input_devices():
            if needle in name.casefold():
                matches.append((idx, name))
        if len(matches) == 1:
            print(f"(Audio: entrada = [{matches[0][0]}] {matches[0][1]})")
            return matches[0][0]
        if len(matches) > 1:
            print(
                f"(Audio: varios microfones contem '{s}'. Escolha o indice em input_device ou refine o texto:)"
            )
            for idx, name in matches:
                print(f"    [{idx}] {name}")
            return None
        print(f"(Audio: nenhum microfone contem '{s}'. Usando predefinido.)")
        print_input_devices()
        return None
    return None


def _validate_device_index(idx: int) -> int | None:
    try:
        d = sd.query_devices(idx)
    except Exception:
        print(f"(Audio: indice invalido {idx}.)")
        return None
    if int(d.get("max_input_channels", 0) or 0) < 1:
        print(f"(Audio: dispositivo [{idx}] nao e entrada de microfone.)")
        return None
    print(f"(Audio: entrada = [{idx}] {d.get('name', '')})")
    return idx
