"""Main VideoDL window."""

from __future__ import annotations

from pathlib import Path
import shutil

from PySide6.QtCore import Qt, QUrl
from PySide6.QtGui import QPixmap
from PySide6.QtNetwork import QNetworkAccessManager, QNetworkRequest
from PySide6.QtWidgets import (
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QComboBox,
    QProgressBar,
    QPushButton,
    QSizePolicy,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
    QHeaderView,
)

from videodl import __version__
from videodl.core.formatting import format_bytes, format_duration, format_eta
from videodl.core.settings import AppSettings
from videodl.core.workers import DownloadWorker, InfoWorker
from videodl.core.ytdlp_service import YtDlpService


QUALITY_PRESETS = [
    ("Best available", "bv*+ba/best"),
    ("4K / up to 2160p", "bv*[height<=2160]+ba/b[height<=2160]"),
    ("1440p or lower", "bv*[height<=1440]+ba/b[height<=1440]"),
    ("1080p or lower", "bv*[height<=1080]+ba/b[height<=1080]"),
    ("720p or lower", "bv*[height<=720]+ba/b[height<=720]"),
    ("480p or lower", "bv*[height<=480]+ba/b[height<=480]"),
    ("360p or lower", "bv*[height<=360]+ba/b[height<=360]"),
]


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("VideoDL")
        self.setMinimumSize(1040, 760)
        self.resize(1280, 880)

        self.settings = AppSettings()
        self.current_info: dict | None = None
        self.info_worker: InfoWorker | None = None
        self.download_worker: DownloadWorker | None = None
        self.active_task: dict | None = None
        self.pending_tasks: list[dict] = []
        self._close_pending = False
        self.network = QNetworkAccessManager(self)

        self._build_ui()
        self._load_settings()
        self._update_engine_status()

    def _card(self) -> tuple[QFrame, QVBoxLayout]:
        frame = QFrame()
        frame.setObjectName("Card")
        layout = QVBoxLayout(frame)
        layout.setContentsMargins(18, 18, 18, 18)
        layout.setSpacing(12)
        return frame, layout

    def _label(self, text: str, object_name: str = "") -> QLabel:
        label = QLabel(text)
        if object_name:
            label.setObjectName(object_name)
        return label

    def _build_ui(self) -> None:
        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(24, 18, 24, 12)
        root.setSpacing(16)

        header = QHBoxLayout()
        brand_mark = self._label("VD", "BrandMark")
        brand_mark.setAlignment(Qt.AlignmentFlag.AlignCenter)
        brand_mark.setFixedSize(42, 42)
        header.addWidget(brand_mark)

        brand_text = QVBoxLayout()
        brand_text.setSpacing(0)
        brand_text.addWidget(self._label("VideoDL", "BrandName"))
        brand_text.addWidget(self._label("PROFESSIONAL DOWNLOAD MANAGER", "Muted"))
        header.addLayout(brand_text)
        header.addStretch(1)

        self.engine_label = self._label("yt-dlp", "Muted")
        header.addWidget(self.engine_label)
        self.version_label = self._label(f"v{__version__}", "Muted")
        header.addWidget(self.version_label)
        root.addLayout(header)

        url_card, url_layout = self._card()
        url_top = QHBoxLayout()
        url_top.addWidget(self._label("Add a video or playlist URL", "SectionTitle"))
        url_top.addStretch(1)
        url_top.addWidget(self._label("Paste a link to analyze available formats", "Muted"))
        url_layout.addLayout(url_top)

        url_row = QHBoxLayout()
        self.url_input = QLineEdit()
        self.url_input.setPlaceholderText("https://…")
        self.url_input.returnPressed.connect(self.analyze_url)
        url_row.addWidget(self.url_input, 1)
        self.analyze_button = QPushButton("Analyze")
        self.analyze_button.setObjectName("Primary")
        self.analyze_button.clicked.connect(self.analyze_url)
        url_row.addWidget(self.analyze_button)
        url_layout.addLayout(url_row)
        root.addWidget(url_card)

        content_row = QHBoxLayout()
        content_row.setSpacing(16)

        preview_card, preview_layout = self._card()
        preview_layout.addWidget(self._label("Media information", "SectionTitle"))
        self.thumbnail_label = self._label("Paste a URL and select Analyze", "Preview")
        self.thumbnail_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.thumbnail_label.setMinimumHeight(225)
        self.thumbnail_label.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        preview_layout.addWidget(self.thumbnail_label, 1)

        self.video_title_label = self._label("No media selected", "SectionTitle")
        self.video_title_label.setWordWrap(True)
        preview_layout.addWidget(self.video_title_label)
        self.video_details_label = self._label(
            "Title, creator, duration and available formats will appear here.",
            "Muted",
        )
        self.video_details_label.setWordWrap(True)
        preview_layout.addWidget(self.video_details_label)
        self.format_count_label = self._label("Waiting for analysis", "Muted")
        preview_layout.addWidget(self.format_count_label)
        content_row.addWidget(preview_card, 3)

        options_card, options_layout = self._card()
        options_layout.addWidget(self._label("Download settings", "SectionTitle"))

        options_layout.addWidget(self._label("Content type", "Muted"))
        self.media_combo = QComboBox()
        self.media_combo.addItem("Video (MP4)", "video")
        self.media_combo.addItem("Audio only (MP3)", "audio")
        options_layout.addWidget(self.media_combo)

        options_layout.addWidget(self._label("Quality", "Muted"))
        self.quality_combo = QComboBox()
        for label, selector in QUALITY_PRESETS:
            self.quality_combo.addItem(label, selector)
        options_layout.addWidget(self.quality_combo)

        options_layout.addWidget(self._label("Save to", "Muted"))
        output_row = QHBoxLayout()
        self.output_input = QLineEdit()
        self.output_input.setPlaceholderText("Download folder")
        output_row.addWidget(self.output_input, 1)
        output_button = QPushButton("Browse")
        output_button.clicked.connect(self.choose_output_dir)
        output_row.addWidget(output_button)
        options_layout.addLayout(output_row)

        options_layout.addWidget(self._label("Cookies (optional)", "Muted"))
        cookie_row = QHBoxLayout()
        self.cookies_input = QLineEdit()
        self.cookies_input.setPlaceholderText("Netscape cookies.txt file")
        cookie_row.addWidget(self.cookies_input, 1)
        cookie_button = QPushButton("Choose")
        cookie_button.clicked.connect(self.choose_cookies_file)
        cookie_row.addWidget(cookie_button)
        options_layout.addLayout(cookie_row)

        options_layout.addWidget(self._label("FFmpeg location (optional)", "Muted"))
        ffmpeg_row = QHBoxLayout()
        self.ffmpeg_input = QLineEdit()
        self.ffmpeg_input.setPlaceholderText("Leave blank to use system PATH")
        ffmpeg_row.addWidget(self.ffmpeg_input, 1)
        ffmpeg_button = QPushButton("Folder")
        ffmpeg_button.clicked.connect(self.choose_ffmpeg_dir)
        ffmpeg_row.addWidget(ffmpeg_button)
        options_layout.addLayout(ffmpeg_row)

        options_layout.addStretch(1)
        action_row = QHBoxLayout()
        self.download_button = QPushButton("Add to queue")
        self.download_button.setObjectName("Primary")
        self.download_button.setEnabled(False)
        self.download_button.clicked.connect(self.add_to_queue)
        action_row.addWidget(self.download_button, 1)
        self.cancel_button = QPushButton("Cancel current")
        self.cancel_button.setObjectName("Danger")
        self.cancel_button.setEnabled(False)
        self.cancel_button.clicked.connect(self.cancel_current)
        action_row.addWidget(self.cancel_button)
        options_layout.addLayout(action_row)
        content_row.addWidget(options_card, 2)

        root.addLayout(content_row, 3)

        queue_card, queue_layout = self._card()
        queue_header = QHBoxLayout()
        queue_header.addWidget(self._label("Download queue", "SectionTitle"))
        queue_header.addStretch(1)
        self.queue_count_label = self._label("0 tasks", "Muted")
        queue_header.addWidget(self.queue_count_label)
        queue_layout.addLayout(queue_header)

        self.queue_table = QTableWidget(0, 5)
        self.queue_table.setHorizontalHeaderLabels(["Name", "Type", "Status", "Progress", "Speed / ETA"])
        self.queue_table.verticalHeader().setVisible(False)
        self.queue_table.setAlternatingRowColors(True)
        self.queue_table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.queue_table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.queue_table.setMinimumHeight(190)
        header_view = self.queue_table.horizontalHeader()
        header_view.setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        header_view.setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        header_view.setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        header_view.setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        header_view.setSectionResizeMode(4, QHeaderView.ResizeMode.ResizeToContents)
        queue_layout.addWidget(self.queue_table)
        root.addWidget(queue_card, 2)

        self.setStatusBar(self.statusBar())
        self.statusBar().showMessage("Ready")

    def _load_settings(self) -> None:
        self.output_input.setText(self.settings.download_dir)
        self.cookies_input.setText(self.settings.cookies_file)
        self.ffmpeg_input.setText(self.settings.ffmpeg_location)

    def _save_settings(self) -> None:
        self.settings.download_dir = self.output_input.text().strip()
        self.settings.cookies_file = self.cookies_input.text().strip()
        self.settings.ffmpeg_location = self.ffmpeg_input.text().strip()

    def _update_engine_status(self) -> None:
        version = YtDlpService.version()
        ffmpeg_ok = YtDlpService.ffmpeg_available(self.ffmpeg_input.text().strip())
        deno_ok = shutil.which("deno") is not None
        self.engine_label.setText(
            f"yt-dlp {version}  •  FFmpeg {'ready' if ffmpeg_ok else 'not found'}"
            f"  •  Deno {'ready' if deno_ok else 'not found'}"
        )

    def choose_output_dir(self) -> None:
        current = self.output_input.text().strip() or str(Path.home())
        selected = QFileDialog.getExistingDirectory(self, "Choose download folder", current)
        if selected:
            self.output_input.setText(selected)
            self._save_settings()

    def choose_cookies_file(self) -> None:
        selected, _ = QFileDialog.getOpenFileName(
            self,
            "Select cookies file",
            str(Path.home()),
            "Cookie files (*.txt);;All files (*)",
        )
        if selected:
            self.cookies_input.setText(selected)
            self._save_settings()

    def choose_ffmpeg_dir(self) -> None:
        selected = QFileDialog.getExistingDirectory(self, "Choose FFmpeg bin folder", str(Path.home()))
        if selected:
            self.ffmpeg_input.setText(selected)
            self._save_settings()
            self._update_engine_status()

    def analyze_url(self) -> None:
        url = self.url_input.text().strip()
        if not url:
            QMessageBox.information(self, "VideoDL", "Paste a video or playlist URL first.")
            return
        if self.info_worker and self.info_worker.isRunning():
            return

        self.analyze_button.setEnabled(False)
        self.download_button.setEnabled(False)
        self.thumbnail_label.setPixmap(QPixmap())
        self.thumbnail_label.setText("Analyzing URL…")
        self.video_title_label.setText("Analyzing…")
        self.video_details_label.setText("Contacting the website and reading media information.")
        self.format_count_label.setText("Please wait")
        self.statusBar().showMessage("Analyzing media…")

        self.info_worker = InfoWorker(url, self.cookies_input.text().strip())
        self.info_worker.succeeded.connect(self.on_info_succeeded)
        self.info_worker.failed.connect(self.on_info_failed)
        self.info_worker.finished.connect(self.on_info_worker_finished)
        self.info_worker.start()

    def on_info_succeeded(self, info: dict) -> None:
        self.current_info = info
        title = info.get("title") or info.get("playlist_title") or "Untitled media"
        self.video_title_label.setText(str(title))

        uploader = info.get("uploader") or info.get("channel") or info.get("creator") or "Unknown creator"
        duration = format_duration(info.get("duration"))
        webpage = info.get("webpage_url") or info.get("original_url") or self.url_input.text().strip()
        details = f"{uploader}   •   {duration}\n{webpage}"
        self.video_details_label.setText(details)

        formats = info.get("formats") or []
        video_heights = [
            int(item["height"])
            for item in formats
            if item.get("height") and str(item.get("height")).isdigit()
        ]
        max_height = max(video_heights, default=0)
        format_count = len(formats)
        playlist_count = info.get("playlist_count")
        if playlist_count:
            self.format_count_label.setText(f"{format_count} formats detected  •  {playlist_count} playlist items")
        else:
            self.format_count_label.setText(
                f"{format_count} formats detected  •  Maximum listed height: {max_height or 'unknown'}p"
            )

        self.quality_combo.clear()
        self.quality_combo.addItem("Best available", "bv*+ba/best")
        for label, selector in QUALITY_PRESETS[1:]:
            self.quality_combo.addItem(label, selector)

        thumbnail = info.get("thumbnail")
        if thumbnail:
            reply = self.network.get(QNetworkRequest(QUrl(str(thumbnail))))
            reply.finished.connect(lambda current_reply=reply: self._thumbnail_finished(current_reply))
        else:
            self.thumbnail_label.setText("No thumbnail available")

        self.download_button.setEnabled(True)
        self.statusBar().showMessage("Media analysis complete")

    def _thumbnail_finished(self, reply) -> None:
        try:
            pixmap = QPixmap()
            pixmap.loadFromData(reply.readAll())
            if not pixmap.isNull():
                self.thumbnail_label.setPixmap(
                    pixmap.scaled(
                        self.thumbnail_label.size(),
                        Qt.AspectRatioMode.KeepAspectRatio,
                        Qt.TransformationMode.SmoothTransformation,
                    )
                )
            else:
                self.thumbnail_label.setText("Thumbnail unavailable")
        finally:
            reply.deleteLater()

    def on_info_failed(self, message: str) -> None:
        self.current_info = None
        self.thumbnail_label.setText("Could not read media information")
        self.video_title_label.setText("Analysis failed")
        self.video_details_label.setText(message)
        self.format_count_label.setText("Check the URL, network, cookies or yt-dlp version.")
        self.statusBar().showMessage("Analysis failed")

    def on_info_worker_finished(self) -> None:
        self.analyze_button.setEnabled(True)
        if self.current_info:
            self.download_button.setEnabled(True)
        self.info_worker = None

    def add_to_queue(self) -> None:
        if not self.current_info:
            QMessageBox.information(self, "VideoDL", "Analyze a URL before adding it to the queue.")
            return

        url = self.url_input.text().strip()
        output_dir = self.output_input.text().strip()
        if not output_dir:
            QMessageBox.warning(self, "VideoDL", "Choose a download folder.")
            return

        audio_only = self.media_combo.currentData() == "audio"
        task = {
            "url": url,
            "title": str(self.current_info.get("title") or self.current_info.get("playlist_title") or url),
            "audio_only": audio_only,
            "output_dir": output_dir,
            "format_selector": str(self.quality_combo.currentData() or "bv*+ba/best"),
            "cookies_file": self.cookies_input.text().strip(),
            "ffmpeg_location": self.ffmpeg_input.text().strip(),
        }

        row = self.queue_table.rowCount()
        self.queue_table.insertRow(row)
        name_item = QTableWidgetItem(task["title"])
        name_item.setToolTip(url)
        self.queue_table.setItem(row, 0, name_item)
        self.queue_table.setItem(row, 1, QTableWidgetItem("MP3" if audio_only else "Video"))
        self.queue_table.setItem(row, 2, QTableWidgetItem("Queued"))

        bar = QProgressBar()
        bar.setRange(0, 100)
        bar.setValue(0)
        bar.setTextVisible(False)
        self.queue_table.setCellWidget(row, 3, bar)
        self.queue_table.setItem(row, 4, QTableWidgetItem("—"))

        task["row"] = row
        self.pending_tasks.append(task)
        self.queue_count_label.setText(f"{self.queue_table.rowCount()} tasks")
        self._save_settings()
        self.statusBar().showMessage("Added to download queue")
        self._start_next_task()

    def _start_next_task(self) -> None:
        if self.download_worker and self.download_worker.isRunning():
            return
        if not self.pending_tasks:
            self.active_task = None
            self.cancel_button.setEnabled(False)
            return

        self.active_task = self.pending_tasks.pop(0)
        row = self.active_task["row"]
        self.queue_table.item(row, 2).setText("Starting")
        self.cancel_button.setEnabled(True)

        self.download_worker = DownloadWorker(self.active_task)
        self.download_worker.progress.connect(self.on_download_progress)
        self.download_worker.completed.connect(self.on_download_completed)
        self.download_worker.finished.connect(self.on_download_worker_finished)
        self.download_worker.start()
        self.statusBar().showMessage("Download started")

    def on_download_progress(self, data: dict) -> None:
        if not self.active_task:
            return
        row = self.active_task["row"]
        status = data.get("status", "")
        if status == "downloading":
            total = data.get("total_bytes") or data.get("total_bytes_estimate")
            downloaded = data.get("downloaded_bytes") or 0
            percent = (float(downloaded) / float(total) * 100) if total else 0
            self._progress_bar(row).setValue(max(0, min(int(percent), 100)))
            speed = format_bytes(data.get("speed")) + "/s"
            eta = format_eta(data.get("eta"))
            self.queue_table.item(row, 2).setText("Downloading")
            self.queue_table.item(row, 4).setText(f"{speed}  •  {eta}")
        elif status == "finished":
            self._progress_bar(row).setValue(100)
            self.queue_table.item(row, 2).setText("Processing")
            self.queue_table.item(row, 4).setText("Merging / finalizing")

    def _progress_bar(self, row: int) -> QProgressBar:
        widget = self.queue_table.cellWidget(row, 3)
        if isinstance(widget, QProgressBar):
            return widget
        bar = QProgressBar()
        bar.setRange(0, 100)
        bar.setTextVisible(False)
        self.queue_table.setCellWidget(row, 3, bar)
        return bar

    def on_download_completed(self, success: bool, message: str) -> None:
        if not self.active_task:
            return
        row = self.active_task["row"]
        if success:
            self.queue_table.item(row, 2).setText("Completed")
            self._progress_bar(row).setValue(100)
            self.queue_table.item(row, 4).setText("Done")
            self.statusBar().showMessage("Download completed")
        elif "cancel" in message.lower():
            self.queue_table.item(row, 2).setText("Cancelled")
            self.queue_table.item(row, 4).setText("Stopped")
            self.statusBar().showMessage(message)
        else:
            self.queue_table.item(row, 2).setText("Failed")
            self.queue_table.item(row, 4).setText(message[:90])
            self.statusBar().showMessage("Download failed: " + message[:180])

    def on_download_worker_finished(self) -> None:
        self.download_worker = None
        self.active_task = None
        self.cancel_button.setEnabled(False)
        self._start_next_task()

    def cancel_current(self) -> None:
        if self.download_worker and self.download_worker.isRunning():
            self.download_worker.requestInterruption()
            self.cancel_button.setEnabled(False)
            self.statusBar().showMessage("Cancellation requested…")

    def closeEvent(self, event) -> None:
        running_workers = [
            worker
            for worker in (self.download_worker, self.info_worker)
            if worker is not None and worker.isRunning()
        ]
        if running_workers:
            if self.download_worker and self.download_worker.isRunning() and not self._close_pending:
                answer = QMessageBox.question(
                    self,
                    "Download in progress",
                    "A download is still running. Cancel it and close VideoDL?",
                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                    QMessageBox.StandardButton.No,
                )
                if answer != QMessageBox.StandardButton.Yes:
                    event.ignore()
                    return

            self._close_pending = True
            for worker in running_workers:
                worker.requestInterruption()
                try:
                    worker.finished.disconnect(self._finish_pending_close)
                except RuntimeError:
                    pass
                worker.finished.connect(self._finish_pending_close)
            self.statusBar().showMessage("Waiting for active tasks to stop safely…")
            event.ignore()
            return

        self._save_settings()
        event.accept()

    def _finish_pending_close(self) -> None:
        if self._close_pending:
            self.close()
