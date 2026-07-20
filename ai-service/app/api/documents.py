from fastapi import APIRouter, File, HTTPException, UploadFile, status

from app.models.document import ParseResponse
from app.parsers.pipeline import DocumentParsingPipeline, UnsupportedDocumentError
from app.services.ocr import OcrUnavailableError

router = APIRouter(prefix="/documents", tags=["document parsing"])
MAX_FILE_BYTES = 100 * 1024 * 1024


@router.post("/parse", response_model=ParseResponse, summary="解析地质文档并完成结构化切分")
def parse_document(file: UploadFile = File(...)) -> ParseResponse:
    content = file.file.read(MAX_FILE_BYTES + 1)
    if not content:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="上传文件为空")
    if len(content) > MAX_FILE_BYTES:
        raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, detail="单个文件不能超过 100 MB")
    try:
        return DocumentParsingPipeline().parse(file.filename or "document", content)
    except UnsupportedDocumentError as exception:
        raise HTTPException(status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, detail=str(exception)) from exception
    except OcrUnavailableError as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception
    except ValueError as exception:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exception)) from exception

