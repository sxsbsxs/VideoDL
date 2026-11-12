# Architecture and implementation notes

## Components

- ui/main_window.py: PySide6 presentation, URL input, media summary, quality controls and queue table.
- core/workers.py: QThread wrappers for metadata extraction and downloads.
- core/ytdlp_service.py: the only module that configures and calls the official yt-dlp Python API.
- core/settings.py: local QSettings preferences.
- core/formatting.py: presentation helpers with unit tests.

## Download lifecycle

1. The user enters a URL and starts analysis.
2. InfoWorker calls YoutubeDL.extract_info with download=False on a worker thread.
3. The interface shows the title, creator, duration, thumbnail and discovered format count.
4. The user selects the content type, quality preset and output folder.
5. A task is appended to the in-memory queue. One DownloadWorker runs at a time.
6. yt-dlp progress hooks update the queue's progress, speed and ETA.
7. FFmpeg is invoked by yt-dlp when separate streams need merging or MP3 extraction is selected.

## Boundaries

- There is no VideoDL backend server. Preferences are stored locally with QSettings.
- The queue is currently in memory and is cleared when the application exits.
- Cancellation is cooperative through yt-dlp progress callbacks. It is not a guaranteed pause/resume implementation. A partial download may be resumable on a later attempt when yt-dlp and the source server support it.
- Playlist links are passed to yt-dlp as playlist URLs. The queue currently treats one entered URL as one queue item.
- Site compatibility, formats and login requirements are controlled by each website and the installed yt-dlp version.
- FFmpeg is not bundled. Install it separately and expose ffmpeg/ffprobe on PATH, or point VideoDL to the FFmpeg bin directory.

## Next engineering milestones

- Persist queue tasks and restore them on restart.
- Add explicit per-task retry and remove actions.
- Add a dedicated playlist preview and item selection dialog.
- Add settings for concurrent fragment downloads and filename templates.
- Add code signing/notarization for release binaries.
- Add platform-specific integration tests and smoke-test packaged executables.
