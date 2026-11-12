import pytest

from videodl.core.ytdlp_service import YtDlpService


def test_extract_info_rejects_empty_url_before_network():
    with pytest.raises(ValueError, match="enter a video URL"):
        YtDlpService().extract_info("")


def test_extract_info_rejects_non_http_url_before_network():
    with pytest.raises(ValueError, match="http://"):
        YtDlpService().extract_info("not-a-url")
