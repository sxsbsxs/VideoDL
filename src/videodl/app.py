"""Application bootstrap."""

from __future__ import annotations

import sys

from PySide6.QtCore import QCoreApplication
from PySide6.QtWidgets import QApplication

from videodl.ui.main_window import MainWindow
from videodl.ui.theme import STYLESHEET


def main() -> int:
    QCoreApplication.setOrganizationName("sxsbsxs")
    QCoreApplication.setApplicationName("VideoDL")

    app = QApplication(sys.argv)
    app.setApplicationName("VideoDL")
    app.setOrganizationName("sxsbsxs")
    app.setStyle("Fusion")
    app.setStyleSheet(STYLESHEET)

    window = MainWindow()
    window.show()
    return app.exec()
