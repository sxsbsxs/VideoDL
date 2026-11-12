"""Dark IDM-inspired desktop theme."""

STYLESHEET = """
QWidget {
    background: #0D1117;
    color: #E6EDF3;
    font-family: "Segoe UI", "Inter", "Noto Sans", sans-serif;
    font-size: 13px;
}
QMainWindow, QDialog {
    background: #0D1117;
}
QFrame#Card {
    background: #151B23;
    border: 1px solid #273244;
    border-radius: 14px;
}
QLabel#BrandMark {
    background: #2F81F7;
    color: #FFFFFF;
    border-radius: 10px;
    font-size: 18px;
    font-weight: 800;
}
QLabel#BrandName {
    font-size: 22px;
    font-weight: 750;
    letter-spacing: 0.2px;
}
QLabel#SectionTitle {
    font-size: 16px;
    font-weight: 700;
    color: #F0F6FC;
}
QLabel#Muted {
    color: #8B949E;
}
QLabel#Preview {
    background: #0B1220;
    border: 1px solid #273244;
    border-radius: 10px;
    color: #718096;
}
QLineEdit, QComboBox {
    background: #0D1420;
    border: 1px solid #303B4D;
    border-radius: 8px;
    padding: 10px 12px;
    selection-background-color: #2F81F7;
    min-height: 20px;
}
QLineEdit:focus, QComboBox:focus {
    border: 1px solid #2F81F7;
}
QComboBox::drop-down {
    border: 0;
    width: 28px;
}
QComboBox QAbstractItemView {
    background: #151B23;
    border: 1px solid #303B4D;
    selection-background-color: #1F4F8E;
    outline: 0;
}
QPushButton {
    background: #212B3A;
    color: #E6EDF3;
    border: 1px solid #35445A;
    border-radius: 8px;
    padding: 9px 14px;
    font-weight: 600;
}
QPushButton:hover {
    background: #29384B;
    border-color: #4B6485;
}
QPushButton:disabled {
    color: #64748B;
    background: #18202B;
    border-color: #263244;
}
QPushButton#Primary {
    background: #2F81F7;
    border: 1px solid #2F81F7;
    color: white;
    padding: 11px 18px;
    font-weight: 700;
}
QPushButton#Primary:hover {
    background: #4A91FA;
}
QPushButton#Danger {
    background: #3B2026;
    border-color: #783B45;
    color: #FFB8C0;
}
QTableWidget {
    background: #101720;
    alternate-background-color: #131D29;
    border: 1px solid #273244;
    border-radius: 10px;
    gridline-color: #202B39;
    selection-background-color: #1E3A5F;
    selection-color: #FFFFFF;
}
QHeaderView::section {
    background: #182230;
    color: #9EADBF;
    border: 0;
    border-bottom: 1px solid #303B4D;
    padding: 10px 8px;
    font-weight: 700;
}
QProgressBar {
    background: #202B39;
    border: 0;
    border-radius: 5px;
    text-align: center;
    color: #E6EDF3;
    min-height: 10px;
    max-height: 10px;
}
QProgressBar::chunk {
    background: #2F81F7;
    border-radius: 5px;
}
QStatusBar {
    color: #8B949E;
    border-top: 1px solid #202B39;
}
QToolTip {
    background: #202B39;
    color: #F0F6FC;
    border: 1px solid #42526A;
    padding: 5px;
}
"""
