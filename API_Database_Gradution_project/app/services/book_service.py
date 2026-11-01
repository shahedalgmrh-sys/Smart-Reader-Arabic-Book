import os
import re
from typing import Optional

from sqlalchemy.orm import Session

from app import crud
from app.config import settings
from app.models import Book


def slugify(value: str) -> str:
    value = value.strip()
    value = re.sub(r"\s+", "_", value)
    value = re.sub(r"[^A-Za-z0-9_\u0600-\u06FF]", "", value)
    if not value:
        return "book"
    return value[:64]


def build_cover_path(title: str, original_filename: str) -> str:
    base_name, ext = os.path.splitext(original_filename)
    safe_title = slugify(title)
    safe_ext = ext if ext.lower() in {".jpg", ".jpeg", ".png", ".gif"} else ".jpg"
    filename = f"cover_{safe_title}{safe_ext}"
    cover_dir = settings.COVER_DIR
    os.makedirs(cover_dir, exist_ok=True)
    return os.path.join(cover_dir, filename)


def create_or_get_book(
    db: Session,
    title: str,
    author: str,
    content: str,
    description: Optional[str],
    cover_image_path: Optional[str],
    device_id: str,
) -> Book:
    return crud.create_book(db, title, author, content, description, cover_image_path, device_id=device_id)
