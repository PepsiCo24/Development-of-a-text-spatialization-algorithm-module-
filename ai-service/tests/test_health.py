from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_endpoint() -> None:
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["phase"] == 1


def test_openapi_is_available() -> None:
    response = client.get("/openapi.json")
    assert response.status_code == 200
    assert response.json()["info"]["title"] == "基于填图对象智能识别的文本空间化算法模块 AI 服务"


def test_text_document_parse_endpoint() -> None:
    response = client.post(
        "/api/v1/documents/parse",
        files={"file": ("survey.txt", "第一章 地层\n\n寒武系灰岩厚约120米。".encode(), "text/plain")},
    )
    assert response.status_code == 200
    assert response.json()["document_type"] == "TXT"
    assert response.json()["chunks"][0]["chapter_title"] == "第一章 地层"
