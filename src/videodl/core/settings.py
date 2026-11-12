"""Persistent desktop preferences."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QSettings


class AppSettings:
    def __init__(self) -> None:
        self._settings = QSettings("sxsbsxs", "VideoDL")

    def _default_download_dir(self) -> str:
        path = Path.home() / "Downloads" / "VideoDL"
        path.mkdir(parents=True, exist_ok=True)
        return str(path)

    @property
    def download_dir(self) -> str:
        value = self._settings.value("download_dir", "", type=str)
        if value and Path(value).exists():
            return value
        return self._default_download_dir()

    @download_dir.setter
    def download_dir(self, value: str) -> None:
        self._settings.setValue("download_dir", value)

    @property
    def cookies_file(self) -> str:
        return self._settings.value("cookies_file", "", type=str)

    @cookies_file.setter
    def cookies_file(self, value: str) -> None:
        self._settings.setValue("cookies_file", value)

    @property
    def ffmpeg_location(self) -> str:
        return self._settings.value("ffmpeg_location", "", type=str)

    @ffmpeg_location.setter
    def ffmpeg_location(self, value: str) -> None:
        self._settings.setValue("ffmpeg_location", value)
