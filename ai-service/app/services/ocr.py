from functools import lru_cache
from io import BytesIO
from typing import Any, Iterable

import numpy as np
from PIL import Image

from app.core.config import get_settings


class OcrUnavailableError(RuntimeError):
    """Raised when PaddleOCR cannot be initialized or execute inference."""


class PaddleOcrService:
    """Local PaddleOCR adapter supporting the current 3.x and legacy result shapes."""

    def recognize(self, image_bytes: bytes) -> str:
        image = Image.open(BytesIO(image_bytes)).convert("RGB")
        engine = self._engine()
        try:
            if hasattr(engine, "predict"):
                results = engine.predict(input=np.asarray(image))
                return self._text_from_v3(results)
            results = engine.ocr(np.asarray(image), cls=True)
            return self._text_from_legacy(results)
        except Exception as exception:
            raise OcrUnavailableError(f"PaddleOCR recognition failed: {exception}") from exception

    @staticmethod
    @lru_cache(maxsize=1)
    def _engine() -> Any:
        settings = get_settings()
        try:
            from paddleocr import PaddleOCR

            return PaddleOCR(
                lang=settings.ocr_language,
                device=settings.ocr_device,
                use_doc_orientation_classify=True,
                use_doc_unwarping=False,
                use_textline_orientation=True,
            )
        except Exception as exception:
            raise OcrUnavailableError(
                "PaddleOCR is unavailable. Install paddleocr and a supported inference engine, then verify model access."
            ) from exception

    def _text_from_v3(self, results: Iterable[Any]) -> str:
        lines: list[str] = []
        for result in results:
            payload = getattr(result, "json", result)
            if callable(payload):
                payload = payload()
            if not isinstance(payload, dict):
                continue
            data = payload.get("res", payload)
            texts = data.get("rec_texts") or data.get("texts") or []
            scores = data.get("rec_scores") or [1.0] * len(texts)
            lines.extend(str(text).strip() for text, score in zip(texts, scores) if str(text).strip() and float(score) >= 0.45)
        return "\n".join(lines)

    def _text_from_legacy(self, results: Iterable[Any]) -> str:
        lines: list[str] = []
        for page in results or []:
            for item in page or []:
                if not isinstance(item, (list, tuple)) or len(item) < 2:
                    continue
                recognition = item[1]
                if isinstance(recognition, (list, tuple)) and recognition:
                    text = str(recognition[0]).strip()
                    score = float(recognition[1]) if len(recognition) > 1 else 1.0
                    if text and score >= 0.45:
                        lines.append(text)
        return "\n".join(lines)


@lru_cache(maxsize=1)
def get_ocr_service() -> PaddleOcrService:
    return PaddleOcrService()

