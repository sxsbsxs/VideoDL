# VideoDL

VideoDL is an open-source desktop video and audio downloader built with Python, PySide6 and the official yt-dlp Python library.

> VideoDL is an independent third-party project. It is not affiliated with, endorsed by, or supported by the yt-dlp project.

## Features

- Desktop application for Windows, Linux and macOS
- yt-dlp Python API as the actual extraction and download engine
- Video metadata and thumbnail preview
- Quality presets, including best available, 4K, 1440p, 1080p, 720p and lower
- Video downloads and MP3 audio extraction
- Playlist and batch URLs supported by yt-dlp
- Sequential download queue with live progress, speed and ETA
- Cookie-file support for sites that require an authenticated session
- User-selectable download directory
- FFmpeg detection and integration for merging and audio post-processing
- GitHub Actions builds for all supported desktop platforms

## Download

Open the GitHub Actions or Releases page:

- Releases: https://github.com/sxsbsxs/VideoDL/releases
- Actions: https://github.com/sxsbsxs/VideoDL/actions

Tagged releases publish platform-specific archives. Builds are not signed/notarized yet, so Windows SmartScreen or macOS Gatekeeper may show an unknown-developer warning.

## Requirements for running from source

- Python 3.11 or newer
- FFmpeg and ffprobe are strongly recommended. They are needed for merging separate video/audio streams and for MP3 conversion.
- A network connection and a supported website

yt-dlp supports many sites, but individual sites can change without notice. Site support and available formats depend on the website, account permissions, region, and the installed yt-dlp version.

## Run from source

### Windows (PowerShell)

    py -3.11 -m venv .venv
    .\.venv\Scripts\Activate.ps1
    python -m pip install --upgrade pip
    python -m pip install -e ".[build]"
    python -m videodl

### Linux / macOS

    python3.11 -m venv .venv
    source .venv/bin/activate
    python -m pip install --upgrade pip
    python -m pip install -e ".[build]"
    python -m videodl

## FFmpeg

VideoDL does not silently download or install FFmpeg. Install FFmpeg using your operating system's package manager or the official FFmpeg distribution, then make sure both ffmpeg and ffprobe are available on PATH.

- Windows: install a trusted FFmpeg build and add its bin directory to PATH.
- macOS: for example, use Homebrew and run `brew install ffmpeg`
- Debian/Ubuntu: run `sudo apt install ffmpeg`

You can verify the installation with:

    ffmpeg -version
    ffprobe -version

Without FFmpeg, some sites may still provide a single-file stream, but separate video/audio streams cannot be merged and MP3 extraction will not work.

## Cookies

Use the Cookie file button to select a Netscape-format cookies.txt file exported from your own browser session. Keep this file private. It can grant access to your account. VideoDL stores only the file path in local application settings; it does not upload the cookie file to VideoDL servers (VideoDL has no backend server).

Do not share cookies, session tokens, or account credentials. Respect each website's terms and content rights.

## Build locally

    python -m pip install -e ".[build]"
    python -m PyInstaller --noconfirm --clean --windowed --name VideoDL --collect-all yt_dlp --collect-all PySide6 src/videodl/__main__.py

The output is written to the dist directory. FFmpeg is not bundled.

## Project structure

    src/videodl/
      core/       yt-dlp service, workers and application settings
      ui/         PySide6 desktop interface
      __main__.py application entry point

## License

The original VideoDL source code is licensed under the MIT License. Third-party components remain under their own licenses. See LICENSE, NOTICE and THIRD_PARTY_LICENSES.

In particular, yt-dlp is distributed under the Unlicense. PySide6 is offered under LGPL-3.0-only, GPL-2.0-only or GPL-3.0-only terms. Review the licenses of the exact packages and any separately distributed binaries you use. This repository does not relicense those projects.

## Responsible use

Use VideoDL only for content you are authorized to download. You are responsible for complying with copyright law, website terms of service, privacy rules and applicable local laws. The project does not bypass DRM or access controls.
