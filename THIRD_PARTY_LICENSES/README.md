# Third-party licenses

VideoDL uses third-party software at runtime. Their licenses remain separate
from the MIT license covering the original VideoDL source code.

| Component | Purpose | License / source |
|---|---|---|
| yt-dlp | Extraction and download engine | Unlicense; https://github.com/yt-dlp/yt-dlp |
| yt-dlp-ejs | YouTube JavaScript challenge scripts | Unlicense AND MIT AND ISC; https://github.com/yt-dlp/ejs |
| PySide6 / Qt for Python | Desktop UI toolkit | LGPL-3.0-only or GPL alternatives; https://wiki.qt.io/Qt_for_Python |
| FFmpeg | Optional media merging and audio conversion | Not bundled; license depends on the user's distribution/build; https://ffmpeg.org/ |

Python package metadata and the license files shipped with the exact versions
used for a build are authoritative. Keep their notices when redistributing.
Do not assume that VideoDL's MIT license applies to these dependencies.
