from io import BytesIO

import fitz
from PIL import Image

from app.parsers.base import RawDocument, RawParagraph
from app.services.ocr import PaddleOcrService


class PdfParser:
    def __init__(self, ocr: PaddleOcrService) -> None:
        self.ocr = ocr

    def parse(self, content: bytes) -> RawDocument:
        paragraphs: list[RawParagraph] = []
        warnings: list[str] = []
        with fitz.open(stream=content, filetype="pdf") as document:
            for page_number, page in enumerate(document, start=1):
                blocks = sorted(page.get_text("blocks"), key=lambda item: (round(item[1], 1), item[0]))
                page_paragraphs = [
                    RawParagraph(page_number, block[4].strip())
                    for block in blocks
                    if len(block) > 6 and block[6] == 0 and block[4].strip()
                ]
                visible_characters = sum(len(item.text) for item in page_paragraphs)
                if visible_characters < 30:
                    pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
                    image = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
                    output = BytesIO()
                    image.save(output, format="PNG")
                    ocr_text = self.ocr.recognize(output.getvalue()).strip()
                    if ocr_text:
                        page_paragraphs = [RawParagraph(page_number, line) for line in ocr_text.splitlines() if line.strip()]
                        warnings.append(f"第 {page_number} 页使用 OCR 识别")
                paragraphs.extend(page_paragraphs)
            return RawDocument("PDF", max(1, document.page_count), paragraphs, warnings)

