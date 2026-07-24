import re

from app.parsers.base import RawDocument, RawParagraph


HEADING_LINE = re.compile(
    r"^(?:第[一二三四五六七八九十百千万0-9]+[章节篇]|[一二三四五六七八九十]+、|\d+(?:\.\d+){0,3}[、.．\s])\s*\S+"
)


class PlainTextParser:
    def parse(self, content: bytes) -> RawDocument:
        text = self._decode(content)
        paragraphs: list[RawParagraph] = []
        for block in re.split(r"\n\s*\n", text.replace("\r\n", "\n").replace("\r", "\n")):
            lines = [line.strip() for line in block.split("\n") if line.strip()]
            if not lines:
                continue
            if HEADING_LINE.match(lines[0]):
                paragraphs.append(RawParagraph(1, lines[0], True))
                if len(lines) > 1:
                    paragraphs.append(RawParagraph(1, "\n".join(lines[1:])))
            else:
                paragraphs.append(RawParagraph(1, "\n".join(lines)))
        return RawDocument("TXT", 1, paragraphs)

    def _decode(self, content: bytes) -> str:
        for encoding in ("utf-8-sig", "gb18030", "utf-16"):
            try:
                return content.decode(encoding)
            except UnicodeDecodeError:
                continue
        return content.decode("utf-8", errors="replace")

