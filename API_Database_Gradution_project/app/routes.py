from fastapi import APIRouter, Depends, File, Form, UploadFile, HTTPException, Header, BackgroundTasks, Query, Request, Response
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from sqlalchemy import text
from app.database import SessionLocal
from app import crud, schemas
from app.services import ai_service
from app.services.book_service import build_cover_path, create_or_get_book
from app.config import settings
from app.utils.text_utils import clean_extracted_text, compute_file_hash_stream
from PyPDF2 import PdfReader
from io import BytesIO
import logging
from typing import List, Optional
import os
import tempfile
import zipfile
import html
import re
import shutil

logger = logging.getLogger("app")

router = APIRouter()

# Handle Vite client requests to prevent 404 errors
@router.get("/@vite/client")
async def vite_client():
    """Return empty response for Vite client requests"""
    return {"message": "Vite client not available in production"}

def _resolve_project_path(path: str) -> str:
    if not path:
        return path
    if os.path.isabs(path):
        return path
    return os.path.abspath(os.path.join(settings.BASE_DIR, path))


def _is_probably_valid_audio_file(path: str) -> bool:
    try:
        if not path or not os.path.exists(path):
            return False
        if os.path.getsize(path) < 128:
            return False
        ext = os.path.splitext(path)[1].lower()
        with open(path, "rb") as f:
            head = f.read(16)
        if ext == ".wav":
            return head.startswith(b"RIFF") and head[8:12] == b"WAVE"
        if ext == ".mp3":
            if head.startswith(b"ID3"):
                return True
            return len(head) >= 2 and head[0] == 0xFF and (head[1] & 0xE0) == 0xE0
        return True
    except Exception:
        return False


def get_db():
    try:
        db = SessionLocal()
    except Exception as e:
        logger.error(f"Database connection error: {e}")
        raise HTTPException(status_code=503, detail="Database Service Unavailable")

    try:
        yield db
    finally:
        db.close()

@router.get("/contact")
def get_contact():
    return {
        "email": os.getenv("CONTACT_EMAIL", "support@example.com"),
        "phone": os.getenv("CONTACT_PHONE", "+1-555-0000"),
        "address": os.getenv("CONTACT_ADDRESS", "Main St, City"),
        "whatsapp": os.getenv("CONTACT_WHATSAPP", "+1-555-1111"),
        "website": os.getenv("CONTACT_WEBSITE", "https://example.com"),
    }

# Helper functions for async file processing
def _extract_pdf_text(file_obj) -> str:
    content = ""
    try:
        head = file_obj.read(4)
        file_obj.seek(0)
        if not head.startswith(b"%PDF"):
            raise HTTPException(status_code=400, detail="Invalid PDF file signature")
        reader = PdfReader(file_obj)
        for page in reader.pages:
            content += (page.extract_text() or "") + "\n"
    except Exception as e:
        logger.error(f"PDF extraction error: {e}")
        raise HTTPException(status_code=400, detail="Invalid PDF file")
    return clean_extracted_text(content)

def _extract_docx_text(file_bytes: bytes) -> str:
    try:
        import docx
        doc = docx.Document(BytesIO(file_bytes))
        raw = "\n".join([para.text or "" for para in doc.paragraphs])
        return clean_extracted_text(raw)
    except Exception as e:
        logger.error(f"DOCX extraction error: {e}")
        raise HTTPException(status_code=400, detail="Invalid DOCX file")


def _strip_html_to_text(html_str: str) -> str:
    text = re.sub(r"(?is)<script.*?>.*?</script>", " ", html_str)
    text = re.sub(r"(?is)<style.*?>.*?</style>", " ", text)
    text = re.sub(r"(?s)<[^>]+>", " ", text)
    text = html.unescape(text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _extract_epub_text(file_bytes: bytes) -> str:
    try:
        zf = zipfile.ZipFile(BytesIO(file_bytes))
        names = [
            n
            for n in zf.namelist()
            if n.lower().endswith((".xhtml", ".html", ".htm"))
            and not n.lower().startswith(("meta-inf/", "__macosx/"))
        ]
        names.sort()
        parts: list[str] = []
        for name in names:
            try:
                raw = zf.read(name)
                decoded = raw.decode("utf-8", errors="ignore")
            except Exception:
                continue
            parts.append(_strip_html_to_text(decoded))
        return clean_extracted_text("\n".join(parts))
    except Exception as e:
        logger.error(f"EPUB extraction error: {e}")
        raise HTTPException(status_code=400, detail="Invalid EPUB file")


def _extract_mobi_text(file_bytes: bytes) -> str:
    tmp_dir = tempfile.mkdtemp(prefix="mobi_extract_")
    mobi_path = os.path.join(tmp_dir, "book.mobi")
    try:
        with open(mobi_path, "wb") as f:
            f.write(file_bytes)
        import mobi  # type: ignore

        out_dir, filepath = mobi.extract(mobi_path)
        extracted_path = filepath
        if not os.path.isabs(extracted_path):
            extracted_path = os.path.join(out_dir, extracted_path)
        if extracted_path.lower().endswith(".pdf"):
            with open(extracted_path, "rb") as f:
                return _extract_pdf_text(f)
        with open(extracted_path, "r", encoding="utf-8", errors="ignore") as f:
            html_text = f.read()
        return clean_extracted_text(_strip_html_to_text(html_text))
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"MOBI extraction error: {e}")
        raise HTTPException(status_code=400, detail="Invalid MOBI file")
    finally:
        try:
            shutil.rmtree(tmp_dir, ignore_errors=True)
        except Exception:
            pass


def _bg_generate_audio(book_id: int, content: str, output_path: str, file_checksum: Optional[str] = None):
    """Background task to generate audio without blocking the response."""
    try:
        logger.info(f"Background generation started for book_id={book_id}, output_path={output_path}")
        real_path = ai_service.generate_tts_audio(content, output_path, book_id=book_id)
        db = SessionLocal()
        try:
            crud.update_book_audio(db, book_id, os.path.abspath(real_path), checksum=file_checksum)
            logger.info(f"Background generation completed for book_id={book_id}, audio_path={real_path}")
        finally:
            db.close()
    except Exception as e:
        logger.error(f"Error generating audio for book {book_id}: {e}", exc_info=True)
        ai_service.update_progress(book_id, 0, f"Failed: {str(e)}")

@router.get("/health")
def health(db: Session = Depends(get_db)):
    try:
        # Simple query to check DB connection
        db.execute(text("SELECT 1"))
        return {"status": "ok", "database": "connected"}
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        raise HTTPException(status_code=503, detail="Service Unavailable: Database connection failed")

@router.get("/my-ip")
def get_client_ip(request: Request):
    return {"ip": request.client.host}


@router.post("/upload")
async def upload_file(
    file: UploadFile = File(None),
    checksum: Optional[str] = Form(None),
    title: Optional[str] = Form(None),
    author: Optional[str] = Form(None),
    description: Optional[str] = Form(None),
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_form: Optional[str] = Form(None, alias="android_id"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db),
):
    checksum_value = (checksum or "").strip()

    if file is None:
        if not checksum_value:
            raise HTTPException(status_code=400, detail="checksum is required")
        audio = crud.get_audiobook_by_checksum(db, checksum_value)
        if not audio:
            raise HTTPException(status_code=404, detail="Not Found")
        resolved_audio_path = _resolve_project_path(audio.file_path)
        if not _is_probably_valid_audio_file(resolved_audio_path):
            raise HTTPException(status_code=404, detail="Not Found")
        ext = os.path.splitext(resolved_audio_path)[1].lower()
        media_type = "audio/mpeg"
        filename = f"audiobook_{audio.audio_id}.mp3"
        if ext == ".wav":
            media_type = "audio/wav"
            filename = f"audiobook_{audio.audio_id}.wav"
        return FileResponse(resolved_audio_path, media_type=media_type, filename=filename)

    device_id = android_id or android_id_q or android_id_form or "unknown"

    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="Empty file")

    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in {".pdf", ".epub", ".mobi", ".docx"}:
        raise HTTPException(status_code=400, detail="Unsupported file type. Use PDF, EPUB, MOBI, or DOCX.")

    if not checksum_value:
        checksum_value = compute_file_hash_stream(BytesIO(file_bytes))

    existing_audio = crud.get_audiobook_by_checksum(db, checksum_value)
    if existing_audio:
        resolved_audio_path = _resolve_project_path(existing_audio.file_path)
        if not _is_probably_valid_audio_file(resolved_audio_path):
            raise HTTPException(status_code=404, detail="Not Found")
        ext = os.path.splitext(resolved_audio_path)[1].lower()
        media_type = "audio/mpeg"
        filename = f"audiobook_{existing_audio.audio_id}.mp3"
        if ext == ".wav":
            media_type = "audio/wav"
            filename = f"audiobook_{existing_audio.audio_id}.wav"
        return FileResponse(resolved_audio_path, media_type=media_type, filename=filename)

    safe_title = (title or "").strip() or os.path.splitext(os.path.basename(file.filename or "book"))[0]
    safe_author = (author or "").strip() or "unknown"

    if ext == ".pdf":
        content = await run_in_threadpool(_extract_pdf_text, BytesIO(file_bytes))
    elif ext == ".epub":
        content = await run_in_threadpool(_extract_epub_text, file_bytes)
    elif ext == ".mobi":
        content = await run_in_threadpool(_extract_mobi_text, file_bytes)
    else:
        content = await run_in_threadpool(_extract_docx_text, file_bytes)

    book = crud.create_book(
        db,
        safe_title,
        safe_author,
        content,
        description,
        None,
        checksum_value,
        device_id=device_id,
    )

    audio_dir = settings.AUDIO_DIR
    os.makedirs(audio_dir, exist_ok=True)
    audio_filename = f"book_{book.pdf_book_id}.wav"
    audio_path = os.path.join(audio_dir, audio_filename)

    real_path = ai_service.generate_tts_audio(content, audio_path, book_id=book.pdf_book_id)
    crud.update_book_audio(db, book.pdf_book_id, os.path.abspath(real_path), checksum=checksum_value)

    resolved_audio_path = _resolve_project_path(os.path.abspath(real_path))
    if not _is_probably_valid_audio_file(resolved_audio_path):
        raise HTTPException(status_code=500, detail="Audio generation failed")
    return FileResponse(resolved_audio_path, media_type="audio/wav", filename=audio_filename)

@router.post("/books", response_model=schemas.BookResponse)
async def add_book(
    title: str = Form(...),
    author: str = Form(...),
    description: str = Form(None),
    file: UploadFile = File(...),
    cover_image: UploadFile = File(None),
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_form: Optional[str] = Form(None, alias="android_id"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    response: Response = None,
    db: Session = Depends(get_db),
):
    cover_image_path = None
    device_id = android_id or android_id_q or android_id_form
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or form field android_id")

    if cover_image:
        file_location = build_cover_path(title, cover_image.filename)
        try:
            data = await cover_image.read()
            with open(file_location, "wb") as f:
                f.write(data)
            cover_image_path = file_location
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid cover image file")

    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="Empty file")
    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in {".pdf", ".epub", ".mobi", ".docx"}:
        raise HTTPException(status_code=400, detail="Unsupported file type. Use PDF, EPUB, MOBI, or DOCX.")

    checksum_value = compute_file_hash_stream(BytesIO(file_bytes))
    existing_audio = crud.get_audiobook_by_checksum(db, checksum_value)
    if existing_audio:
        resolved_audio_path = _resolve_project_path(existing_audio.file_path)
        if not _is_probably_valid_audio_file(resolved_audio_path):
            existing_audio = None

    existing = crud.get_book_by_file_checksum_for_device(db, device_id, checksum_value)
    if existing:
        existing.file_checksum = checksum_value
        existing.book_pdf_name = title
        existing.book_pdf_author = author
        if description is not None and description != "":
            existing.description = description
        if cover_image_path:
            existing.cover_image = cover_image_path
        if existing_audio and not existing.audio_id:
            existing.audio_id = existing_audio.audio_id
        db.commit()
        db.refresh(existing)
        if response is not None:
            response.headers["X-Book-Already-Exists"] = "1"
        return existing

    if ext == ".pdf":
        content = await run_in_threadpool(_extract_pdf_text, BytesIO(file_bytes))
    elif ext == ".epub":
        content = await run_in_threadpool(_extract_epub_text, file_bytes)
    elif ext == ".mobi":
        content = await run_in_threadpool(_extract_mobi_text, file_bytes)
    else:
        content = await run_in_threadpool(_extract_docx_text, file_bytes)

    book = crud.create_book(
        db,
        title,
        author,
        content,
        description,
        cover_image_path,
        checksum_value,
        device_id=device_id,
    )
    if existing_audio and not book.audio_id:
        book.audio_id = existing_audio.audio_id
        db.commit()
        db.refresh(book)
    return book

@router.post("/books/{book_id}/generate_audio")
def generate_audio(
    book_id: int, 
    background_tasks: BackgroundTasks,
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db)
):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or query param android_id")
    db_book = crud.get_book_by_id(db, book_id)
    if not db_book:
        raise HTTPException(status_code=404, detail="Book not found")
    # Allow access if device_id is "unknown" (app couldn't get proper device ID)
    # or if the device_id matches the book's device_id
    if device_id != "unknown" and db_book.device_id != device_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    
    if db_book.audio_path:
        resolved_path = _resolve_project_path(db_book.audio_path)
        if os.path.exists(resolved_path) and _is_probably_valid_audio_file(resolved_path):
            return {
                "message": "Audio already exists",
                "audio_path": db_book.audio_path,
                "status": "completed",
                "progress": 100,
                "estimated_time_seconds": 0
            }

    if getattr(db_book, "file_checksum", None):
        existing_audio = crud.get_audiobook_by_checksum(db, db_book.file_checksum)
        if existing_audio:
            resolved_audio_path = _resolve_project_path(existing_audio.file_path)
            if _is_probably_valid_audio_file(resolved_audio_path):
                db_book.audio_id = existing_audio.audio_id
                db.commit()
                db.refresh(db_book)
                return {
                    "message": "Audio already exists",
                    "audio_path": db_book.audio_path,
                    "status": "completed",
                    "progress": 100,
                    "estimated_time_seconds": 0
                }

    audio_dir = settings.AUDIO_DIR
    os.makedirs(audio_dir, exist_ok=True)
    audio_filename = f"book_{book_id}.wav"
    audio_path = os.path.join(audio_dir, audio_filename)

    cleaned_text = ai_service._normalize_tts_text(db_book.content)
    segments_count = len(ai_service.split_text_into_segments(cleaned_text))
    estimated_time = ai_service.estimate_time(cleaned_text)

    ai_service.update_progress(book_id, 0, "Starting")

    background_tasks.add_task(_bg_generate_audio, book_id, db_book.content, audio_path, getattr(db_book, "file_checksum", None))

    return {
        "message": "Audio generation started",
        "audio_path": audio_path,
        "segments_count": segments_count,
        "status": "processing",
        "estimated_time_seconds": estimated_time
    }

@router.get("/books/{book_id}/progress")
def get_audio_progress(
    book_id: int,
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db)
):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required")

    db_book = crud.get_book_by_id(db, book_id)
    if not db_book:
        raise HTTPException(status_code=404, detail="Book not found")
    # Allow access if device_id is "unknown" (app couldn't get proper device ID)
    # or if the device_id matches the book's device_id
    if device_id != "unknown" and db_book.device_id != device_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    
    # If audio_path exists in DB, we are done (unless we are re-generating, but simplicity first)
    if db_book.audio_path and os.path.exists(_resolve_project_path(db_book.audio_path)):
         return {"progress": 100, "status": "completed", "eta": 0}

    # Otherwise check in-memory progress
    progress_data = ai_service.get_progress(book_id)
    return progress_data


@router.get("/books/{book_id}/audio")
def get_audio(book_id: int, android_id: Optional[str] = Header(None, alias="X-Device-ID"), android_id_q: Optional[str] = Query(None, alias="android_id"), db: Session = Depends(get_db)):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or query param android_id")
    db_book = crud.get_book_by_id(db, book_id)
    resolved_audio_path = _resolve_project_path(db_book.audio_path) if db_book and db_book.audio_path else ""
    if not db_book:
        raise HTTPException(status_code=404, detail="Book not found")
    if not db_book.audio_path or not os.path.exists(resolved_audio_path):
        raise HTTPException(status_code=404, detail="Audio not found")
    if not _is_probably_valid_audio_file(resolved_audio_path):
        raise HTTPException(status_code=404, detail="Audio not found")
    # Allow access if device_id is "unknown" (app couldn't get proper device ID)
    # or if the device_id matches the book's device_id
    if device_id != "unknown" and db_book.device_id != device_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    
    ext = os.path.splitext(resolved_audio_path)[1].lower()
    media_type = "audio/mpeg"
    filename = f"book_{book_id}.mp3"
    if ext == ".wav":
        media_type = "audio/wav"
        filename = f"book_{book_id}.wav"

    return FileResponse(resolved_audio_path, media_type=media_type, filename=filename)

@router.get("/books/{book_id}/cover")
def get_cover(book_id: int, android_id: Optional[str] = Header(None, alias="X-Device-ID"), android_id_q: Optional[str] = Query(None, alias="android_id"), db: Session = Depends(get_db)):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or query param android_id")
    db_book = crud.get_book_by_id(db, book_id)
    if not db_book:
        raise HTTPException(status_code=404, detail="Book not found")
    cover_path = db_book.cover_image
    if not cover_path or not os.path.exists(cover_path):
        fallback = settings.DEFAULT_COVER_PATH
        if not os.path.exists(fallback):
            raise HTTPException(status_code=404, detail="Cover image not found")
        cover_path = fallback
    # Allow access if device_id is "unknown" (app couldn't get proper device ID)
    # or if the device_id matches the book's device_id
    if device_id != "unknown" and db_book.device_id != device_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    
    # Determine media type based on extension
    ext = os.path.splitext(cover_path)[1].lower()
    media_type = "image/jpeg"
    if ext == ".png":
        media_type = "image/png"
    elif ext == ".gif":
        media_type = "image/gif"
        
    return FileResponse(cover_path, media_type=media_type)


@router.post("/books/json", response_model=schemas.BookResponse)
def add_book_json(
    book: schemas.BookCreatePayload,
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db),
):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(
            status_code=400,
            detail="Device ID required via header X-Device-ID or query param android_id",
        )
    return create_or_get_book(db, book.title, book.author, book.content, book.description, None, device_id)

@router.put("/books/{book_id}", response_model=schemas.BookResponse)
def update_book(
    book_id: int,
    book_update: schemas.BookUpdate,
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db)
):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required")
    
    db_book = crud.update_book(db, book_id, device_id, book_update.title, book_update.author, book_update.description)
    if not db_book:
        raise HTTPException(status_code=404, detail="Book not found or access denied")
    return db_book

@router.get("/books", response_model=List[schemas.BookResponse])
def read_books(
    q: Optional[str] = None,
    limit: int = 10,
    offset: int = 0,
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db),
):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or query param android_id")
    if q:
        return crud.search_books_by_device(db, device_id=device_id, query=q, limit=limit, offset=offset)
    return crud.get_books_paginated_by_device(db, device_id=device_id, limit=limit, offset=offset)

@router.get("/books/count")
def count_books(
    q: Optional[str] = None,
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db),
):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or query param android_id")
    if q:
        return {"count": crud.count_books_search_by_device(db, device_id=device_id, query=q)}
    return {"count": crud.count_books_by_device(db, device_id=device_id)}

@router.get("/books/{book_id}", response_model=schemas.BookDetail)
def read_book(book_id: int, android_id: Optional[str] = Header(None, alias="X-Device-ID"), android_id_q: Optional[str] = Query(None, alias="android_id"), db: Session = Depends(get_db)):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or query param android_id")
    db_book = crud.get_book_by_id(db, book_id=book_id)
    if db_book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    # Allow access if device_id is "unknown" (app couldn't get proper device ID)
    # or if the device_id matches the book's device_id
    if device_id != "unknown" and db_book.device_id != device_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    return db_book

@router.put("/books/{book_id}/progress")
def update_progress(
    book_id: int,
    last_page: int = Query(..., ge=1),
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db)
):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required")
    
    db_book = crud.update_book_progress(db, book_id, last_page, device_id)
    if not db_book:
        raise HTTPException(status_code=404, detail="Book not found or access denied")
    return {"message": "Progress updated", "last_page": last_page}

@router.get("/books/{book_id}/audio_position")
def get_audio_position(
    book_id: int,
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db),
):
    device_id = android_id or android_id_q
    if not device_id or device_id == "unknown":
        raise HTTPException(status_code=400, detail="Device ID required")

    pos = crud.get_audio_position_ms(db, book_id=book_id, device_id=device_id)
    if pos is None:
        raise HTTPException(status_code=404, detail="Book not found or access denied")
    return {"book_id": book_id, "position_ms": pos}


@router.put("/books/{book_id}/audio_position")
def update_audio_position(
    book_id: int,
    position_ms: int = Query(..., ge=0),
    android_id: Optional[str] = Header(None, alias="X-Device-ID"),
    android_id_q: Optional[str] = Query(None, alias="android_id"),
    db: Session = Depends(get_db),
):
    device_id = android_id or android_id_q
    if not device_id or device_id == "unknown":
        raise HTTPException(status_code=400, detail="Device ID required")

    saved = crud.update_audio_position_ms(db, book_id=book_id, position_ms=position_ms, device_id=device_id)
    if saved is None:
        raise HTTPException(status_code=404, detail="Book not found or access denied")
    return {"message": "Audio position updated", "position_ms": saved}

@router.delete("/books/{book_id}")
def delete_book(book_id: int, android_id: Optional[str] = Header(None, alias="X-Device-ID"), android_id_q: Optional[str] = Query(None, alias="android_id"), db: Session = Depends(get_db)):
    device_id = android_id or android_id_q
    if not device_id:
        raise HTTPException(status_code=400, detail="Device ID required via header X-Device-ID or query param android_id")
    db_book = crud.delete_book_for_device(db, book_id, device_id=device_id)
    if db_book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    return {"message": "Book deleted successfully"}

# Catch-all route for any Vite-related requests
@router.get("/{vite_path:path}")
async def catch_vite_requests(vite_path: str):
    """Catch any remaining Vite-related requests and return empty response"""
    if vite_path.startswith("@vite/") or "vite" in vite_path.lower():
        return {"message": "Vite development features not available in production"}
    raise HTTPException(status_code=404, detail="Not found")
