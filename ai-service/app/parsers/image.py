from app.parsers.base import RawDocument, RawParagraph
from app.services.ocr import PaddleOcrService


class ImageParser:
    def __init__(self, ocr: PaddleOcrService) -> None:
        self.ocr = ocr

    def parse(self, content: bytes) -> RawDocument:
        text = self.ocr.recognize(content)
        paragraphs = [RawParagraph(1, line.strip()) for line in text.splitlines() if line.strip()]
        return RawDocument("IMAGE", 1, paragraphs, ["图片使用 PaddleOCR 识别"])

