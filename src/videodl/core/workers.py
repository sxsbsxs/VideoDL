"""Background QThreads so network and download work never blocks the UI."""

from __future__ import annotations

from PySide6.QtCore import QThread, Signal
from yt_dlp.utils import DownloadError

from videodl.core.ytdlp_service import YtDlpService


class InfoWorker(QThread):
    succeeded = Signal(dict)
    failed = Signal(str)

    def __init__(self, url: str, cookies_file: str = "") -> None:
        super().__init__()
        self.url = url
        self.cookies_file = cookies_file

    def run(self) -> None:
        try:
            info = YtDlpService().extract_info(self.url, self.cookies_file)
            self.succeeded.emit(info)
        except Exception as exc:
            self.failed.emit(str(exc))


class DownloadWorker(QThread):
    progress = Signal(dict)
    completed = Signal(bool, str)

    def __init__(self, task: dict) -> None:
        super().__init__()
        self.task = task

    def run(self) -> None:
        service = YtDlpService()
        try:
            service.download(
                url=self.task["url"],
                output_dir=self.task["output_dir"],
                format_selector=self.task["format_selector"],
                audio_only=self.task["audio_only"],
                cookies_file=self.task.get("cookies_file", ""),
                ffmpeg_location=self.task.get("ffmpeg_location", ""),
                progress_callback=self.progress.emit,
                cancel_check=self.isInterruptionRequested,
            )
            if self.isInterruptionRequested():
                self.completed.emit(False, "Download cancelled.")
            else:
                self.completed.emit(True, "Download completed.")
        except DownloadError as exc:
            if self.isInterruptionRequested():
                self.completed.emit(False, "Download cancelled. Partial files may be resumable.")
            else:
                self.completed.emit(False, str(exc))
        except Exception as exc:
            self.completed.emit(False, str(exc))
