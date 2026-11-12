# Third-party licenses

VideoDL uses third-party software at runtime. Their licenses remain separate
from the MIT license covering the original VideoDL source code.

| Component | Purpose | License / source |
|---|---|---|
| yt-dlp | Extraction and download engine | Unlicense; https://github.com/yt-dlp/yt-dlp |
| yt-dlp-ejs | YouTube JavaScript challenge scripts | Unlicense AND MIT AND ISC; https://github.com/yt-dlp/ejs |
| PySide6 Essentials / Qt for Python | Qt Widgets and Qt Network UI toolkit modules | LGPL-3.0-only or GPL alternatives; https://wiki.qt.io/Qt_for_Python |
| FFmpeg | Optional desktop media merging and audio conversion | Not bundled; license depends on the user's distribution/build; https://ffmpeg.org/ |
| Chaquopy | Embedded Python runtime for Android | MIT; https://chaquo.com/chaquopy/license/ |
| AndroidX / Jetpack Compose | Android UI, lifecycle and platform support | Apache-2.0; https://developer.android.com/jetpack/androidx |
| Coil | Android thumbnail loading | Apache-2.0; https://github.com/coil-kt/coil |

Python package metadata and the license files shipped with the exact versions
used for a build are authoritative. Keep their notices when redistributing.
Do not assume that VideoDL's MIT license applies to these dependencies.
