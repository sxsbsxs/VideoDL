"""Small formatting helpers shared by the UI and tests."""

from __future__ import annotations


def format_bytes(value: float | int | None) -> str:
    if value is None:
        return "—"
    amount = max(float(value), 0.0)
    units = ("B", "KB", "MB", "GB", "TB")
    for unit in units:
        if amount < 1024 or unit == units[-1]:
            if unit == "B":
                return f"{int(amount)} {unit}"
            return f"{amount:.1f} {unit}"
        amount /= 1024
    return "—"


def format_eta(seconds: float | int | None) -> str:
    if seconds is None:
        return "—"
    total = max(int(seconds), 0)
    hours, remainder = divmod(total, 3600)
    minutes, secs = divmod(remainder, 60)
    if hours:
        return f"{hours:d}:{minutes:02d}:{secs:02d}"
    return f"{minutes:d}:{secs:02d}"


def format_duration(seconds: float | int | None) -> str:
    if seconds is None:
        return "Duration unavailable"
    total = max(int(seconds), 0)
    hours, remainder = divmod(total, 3600)
    minutes, secs = divmod(remainder, 60)
    if hours:
        return f"{hours:d}:{minutes:02d}:{secs:02d}"
    return f"{minutes:d}:{secs:02d}"
