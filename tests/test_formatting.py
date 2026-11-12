from videodl.core.formatting import format_bytes, format_duration, format_eta


def test_format_bytes():
    assert format_bytes(None) == "—"
    assert format_bytes(512) == "512 B"
    assert format_bytes(1024) == "1.0 KB"
    assert format_bytes(1024 * 1024) == "1.0 MB"


def test_format_eta():
    assert format_eta(None) == "—"
    assert format_eta(8) == "0:08"
    assert format_eta(65) == "1:05"
    assert format_eta(3661) == "1:01:01"


def test_format_duration():
    assert format_duration(None) == "Duration unavailable"
    assert format_duration(83) == "1:23"
    assert format_duration(3723) == "1:02:03"
