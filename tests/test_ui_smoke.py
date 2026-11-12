import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtWidgets import QApplication

from videodl.ui.main_window import MainWindow


def test_main_window_can_initialize():
    app = QApplication.instance() or QApplication([])
    window = MainWindow()

    assert window.windowTitle() == "VideoDL"
    assert window.url_input is not None
    assert window.analyze_button.isEnabled()
    assert not window.download_button.isEnabled()
    assert window.queue_table.rowCount() == 0

    window.close()
    app.processEvents()
