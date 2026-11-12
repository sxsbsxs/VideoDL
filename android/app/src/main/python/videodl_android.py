"""Android bridge for the official yt-dlp Python package.

The Kotlin layer calls these functions through Chaquopy. No shell or backend
server is used: yt-dlp runs in-process inside the Android application.
"""

from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

import yt_dlp
from yt_dlp.version import __version__ as YTDLP_VERSION
from yt_dlp.utils import DownloadError


def _clean_cookie_path(cookie_path: str | None) -> str | None:
    if not cookie_path:
        return None
    path = Path(cookie_path).expanduser()
    if not path.is_file():
        raise FileNotFoundError("Cookie file does not exist.")
    return str(path)


def _base_options(cookie_path: str | None = None) -> dict[str, Any]:
    options: dict[str, Any] = {
        "quiet": True,
        "no_warnings": False,
        "noprogress": True,
        "socket_timeout": 25,
        "retries": 3,
        "extractor_retries": 3,
    }
    cookie = _clean_cookie_path(cookie_path)
    if cookie:
        options["cookiefile"] = cookie
    return options


def engine_version() -> str:
    return YTDLP_VERSION


def analyze(url: str, cookie_path: str = "") -> str:
    """Read metadata and available formats without downloading the media."""
    url = (url or "").strip()
    if not url:
        raise ValueError("请输入视频链接。")
    if not re.match(r"^https?://", url, flags=re.IGNORECASE):
        raise ValueError("链接必须以 http:// 或 https:// 开头。")

    options = _base_options(cookie_path)
    options.update({
        "skip_download": True,
        "extract_flat": False,
        "noplaylist": True,
    })

    with yt_dlp.YoutubeDL(options) as downloader:
        info = downloader.extract_info(url, download=False)

    formats = []
    max_height = 0
    for item in (info.get("formats") or []):
        height = item.get("height")
        if isinstance(height, int):
            max_height = max(max_height, height)
        formats.append({
            "format_id": str(item.get("format_id") or ""),
            "ext": str(item.get("ext") or ""),
            "height": height,
            "resolution": str(item.get("resolution") or ""),
            "format_note": str(item.get("format_note") or ""),
            "filesize": item.get("filesize") or item.get("filesize_approx"),
            "vcodec": str(item.get("vcodec") or ""),
            "acodec": str(item.get("acodec") or ""),
        })

    payload = {
        "id": str(info.get("id") or ""),
        "title": str(info.get("title") or "未命名视频"),
        "uploader": str(info.get("uploader") or info.get("channel") or ""),
        "duration": info.get("duration"),
        "thumbnail": str(info.get("thumbnail") or ""),
        "webpage_url": str(info.get("webpage_url") or url),
        "format_count": len(formats),
        "max_height": max_height,
        "formats": formats[:80],
    }
    return json.dumps(payload, ensure_ascii=False)


def download(
    url: str,
    output_dir: str,
    format_selector: str,
    audio_only: bool,
    cookie_path: str,
    callback: Any,
) -> str:
    """Download with live progress callbacks to a Kotlin object."""
    url = (url or "").strip()
    if not re.match(r"^https?://", url, flags=re.IGNORECASE):
        raise ValueError("无效的视频链接。")

    destination = Path(output_dir).expanduser()
    destination.mkdir(parents=True, exist_ok=True)
    before = {item.name for item in destination.iterdir() if item.is_file()}

    options = _base_options(cookie_path)
    options.update({
        "format": "bestaudio/best" if audio_only else (format_selector or "best[ext=mp4]/best"),
        "outtmpl": str(destination / "%(title).180B [%(id)s].%(ext)s"),
        "noplaylist": False,
        "continuedl": True,
        "nopart": False,
        "overwrites": False,
        "windowsfilenames": True,
        "retries": 8,
        "fragment_retries": 8,
        "socket_timeout": 30,
        "concurrent_fragment_downloads": 2,
    })

    def report(data: dict[str, Any]) -> None:
        if callback.shouldCancel():
            raise DownloadError("用户取消了下载。")

        status = str(data.get("status") or "")
        total = data.get("total_bytes") or data.get("total_bytes_estimate")
        downloaded = data.get("downloaded_bytes") or 0
        percent = (float(downloaded) / float(total) * 100.0) if total else 0.0

        payload = {
            "status": status,
            "percent": max(0.0, min(percent, 100.0)),
            "downloaded_bytes": downloaded,
            "total_bytes": total,
            "speed": data.get("speed"),
            "eta": data.get("eta"),
            "filename": data.get("filename") or "",
        }
        callback.onProgress(json.dumps(payload, ensure_ascii=False))

    options["progress_hooks"] = [report]
    options["postprocessor_hooks"] = [report]

    with yt_dlp.YoutubeDL(options) as downloader:
        result = downloader.download([url])
        if result:
            raise DownloadError(f"yt-dlp 返回错误代码：{result}")

    created = []
    for item in destination.iterdir():
        if item.is_file() and item.name not in before and not item.name.endswith(".part"):
            created.append(str(item))

    return json.dumps({"files": created}, ensure_ascii=False)
