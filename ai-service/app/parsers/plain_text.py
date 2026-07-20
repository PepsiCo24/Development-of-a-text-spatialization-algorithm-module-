from app.parsers.base import RawDocument, RawParagraph


class PlainTextParser:
    def parse(self, content: bytes) -> RawDocument:
        text = self._decode(content)
        paragraphs = [RawParagraph(1, value.strip()) for value in text.replace("\r\n", "\n").split("\n\n") if value.strip()]
        return RawDocument("TXT", 1, paragraphs)

    def _decode(self, content: bytes) -> str:
        for encoding in ("utf-8-sig", "gb18030", "utf-16"):
            try:
                return content.decode(encoding)
            except UnicodeDecodeError:
                continue
        return content.decode("utf-8", errors="replace")

