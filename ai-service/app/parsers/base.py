from dataclasses import dataclass, field


@dataclass(slots=True)
class RawParagraph:
    page: int
    text: str
    is_heading: bool = False


@dataclass(slots=True)
class RawDocument:
    document_type: str
    page_count: int
    paragraphs: list[RawParagraph]
    warnings: list[str] = field(default_factory=list)

