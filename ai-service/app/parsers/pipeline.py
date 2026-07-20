import re
import unicodedata
from pathlib import Path

from app.core.config import get_settings
from app.models.document import DocumentChunk, ParseResponse
from app.parsers.base import RawDocument, RawParagraph
from app.parsers.image import ImageParser
from app.parsers.pdf import PdfParser
from app.parsers.plain_text import PlainTextParser
from app.parsers.word import WordParser
from app.services.ocr import PaddleOcrService, get_ocr_service

HEADING_PATTERN = re.compile(
    r"^(?:第[一二三四五六七八九十百千万0-9]+[章节篇]|[一二三四五六七八九十]+、|\d+(?:\.\d+){0,3}[、.．\s])\s*\S+"
)


class UnsupportedDocumentError(ValueError):
    pass


class DocumentParsingPipeline:
    def __init__(self, ocr: PaddleOcrService | None = None, chunk_size: int | None = None) -> None:
        self.ocr = ocr or get_ocr_service()
        self.chunk_size = chunk_size or get_settings().parse_chunk_size

    def parse(self, filename: str, content: bytes) -> ParseResponse:
        suffix = Path(filename).suffix.lower()
        parser = self._parser(suffix)
        raw = parser.parse(content)
        chunks = self._structure(raw)
        if not chunks:
            raise ValueError("未能从文档中提取有效文本")
        return ParseResponse(
            filename=filename,
            document_type=raw.document_type,
            page_count=raw.page_count,
            chunks=chunks,
            warnings=raw.warnings,
        )

    def _parser(self, suffix: str):
        if suffix == ".pdf":
            return PdfParser(self.ocr)
        if suffix in {".doc", ".docx"}:
            if suffix == ".doc":
                raise UnsupportedDocumentError("旧版 DOC 暂不支持直接解析，请另存为 DOCX 后重试")
            return WordParser()
        if suffix == ".txt":
            return PlainTextParser()
        if suffix in {".png", ".jpg", ".jpeg", ".tif", ".tiff"}:
            return ImageParser(self.ocr)
        raise UnsupportedDocumentError(f"不支持的文档格式: {suffix or '无扩展名'}")

    def _structure(self, raw: RawDocument) -> list[DocumentChunk]:
        chunks: list[DocumentChunk] = []
        chapter: str | None = None
        buffer: list[RawParagraph] = []
        length = 0

        def flush() -> None:
            nonlocal buffer, length
            if not buffer:
                return
            content = "\n\n".join(item.text for item in buffer).strip()
            if content:
                chunks.append(DocumentChunk(
                    chunk_index=len(chunks), chapter_title=chapter, content=content,
                    page_start=min(item.page for item in buffer), page_end=max(item.page for item in buffer), char_count=len(content),
                ))
            buffer = []
            length = 0

        for paragraph in raw.paragraphs:
            text = self._clean(paragraph.text)
            if not text:
                continue
            if paragraph.is_heading or HEADING_PATTERN.match(text):
                flush()
                chapter = text[:255]
                continue
            if length and length + len(text) + 2 > self.chunk_size:
                flush()
            if len(text) > self.chunk_size:
                for start in range(0, len(text), self.chunk_size):
                    part = text[start:start + self.chunk_size]
                    buffer = [RawParagraph(paragraph.page, part)]
                    length = len(part)
                    flush()
                continue
            buffer.append(RawParagraph(paragraph.page, text))
            length += len(text) + (2 if length else 0)
        flush()
        return chunks

    def _clean(self, text: str) -> str:
        normalized = unicodedata.normalize("NFKC", text)
        normalized = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", "", normalized)
        lines = [re.sub(r"[ \t]+", " ", line).strip() for line in normalized.replace("\r", "\n").split("\n")]
        return "\n".join(line for line in lines if line).strip()

