"""Thin, testable adapter around the official yt-dlp Python API."""

from __future__ import annotations

import os
import shutil
from pathlib import Path
from typing import Callable

import yt_dlp
from yt_dlp.utils import DownloadError
from yt_dlp.version import __version__ as YTDLP_VERSION


ProgressCallback = Callable[[dict], None]
CancelCheck = Callable[[], bool]


class YtDlpService:
    """Owns yt-dlp options and keeps them out of the UI layer."""

    @staticmethod
    def version() -> str:
        return YTDLP_VERSION

    @staticmethod
    def ffmpeg_available(location: str = "") -> bool:
        if location:
            candidate = Path(location)
            if candidate.is_dir():
                executable = "ffmpeg.exe" if os.name == "nt" else "ffmpeg"
                return (candidate / executable).is_file()
            return candidate.is_file()
        return shutil.which("ffmpeg") is not None

    def extract_info(self, url: str, cookies_file: str = "") -> dict:
        url = url.strip()
        if not url:
            raise ValueError("Please enter a video URL.")
        if not url.lower().startswith(("https://", "http://")):
            raise ValueError("The URL must start with http:// or https://.")

        options: dict = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": False,
            "skip_download": True,
            "socket_timeout": 20,
            "retries": 3,
        }
        if cookies_file:
            cookie_path = Path(cookies_file).expanduser()
            if not cookie_path.is_file():
                raise FileNotFoundError(f"Cookie file not found: {cookie_path}")
            options["cookiefile"] = str(cookie_path)

        with yt_dlp.YoutubeDL(options) as downloader:
            info = downloader.extract_info(url, download=False)
            return downloader.sanitize_info(info)

    def download(
        self,
        url: str,
        output_dir: str,
        format_selector: str,
        audio_only: bool,
        cookies_file: str = "",
        ffmpeg_location: str = "",
        progress_callback: ProgressCallback | None = None,
        cancel_check: CancelCheck | None = None,
    ) -> None:
        destination = Path(output_dir).expanduser()
        destination.mkdir(parents=True, exist_ok=True)

        options: dict = {
            "format": "bestaudio/best" if audio_only else format_selector,
            "outtmpl": str(destination / "%(title).180B [%(id)s].%(ext)s"),
            "noplaylist": False,
            "continuedl": True,
            "nopart": False,
            "overwrites": False,
            "windowsfilenames": True,
            "quiet": True,
            "no_warnings": True,
            "retries": 10,
            "fragment_retries": 10,
            "socket_timeout": 30,
            "concurrent_fragment_downloads": 4,
        }

        if cookies_file:
            cookie_path = Path(cookies_file).expanduser()
            if not cookie_path.is_file():
                raise FileNotFoundError(f"Cookie file not found: {cookie_path}")
            options["cookiefile"] = str(cookie_path)

        if ffmpeg_location:
            options["ffmpeg_location"] = ffmpeg_location

        if audio_only:
            options["postprocessors"] = [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": "192",
                }
            ]
        else:
            options["merge_output_format"] = "mp4"

        def on_progress(data: dict) -> None:
            if cancel_check and cancel_check():
                raise DownloadError("Download cancelled by user")
            if progress_callback:
                progress_callback(data)

        options["progress_hooks"] = [on_progress]

        with yt_dlp.YoutubeDL(options) as downloader:
            result = downloader.download([url])
            if result:
                raise DownloadError(f"yt-dlp returned exit code {result}")
