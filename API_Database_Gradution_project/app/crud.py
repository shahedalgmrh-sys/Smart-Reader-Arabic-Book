import os
from datetime import datetime
from typing import Optional

from sqlalchemy import func
from sqlalchemy.orm import Session, selectinload

from app.models import AudioBook, PdfBook, Position, User
from app.utils.text_utils import compute_file_hash_stream


def get_or_create_user_by_device(db: Session, device_id: str, nickname: Optional[str] = None) -> User:
    device_id = (device_id or "").strip()
    user = db.query(User).filter(User.device_id == device_id).first()
    if user:
        return user
    user = User(
        device_id=device_id,
        nick_name=nickname or (f"user_{device_id}" if device_id else "anonymous_user"),
        created_at=datetime.utcnow(),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def get_book_by_title_author_for_device(db: Session, device_id: str, title: str, author: str) -> Optional[PdfBook]:
    title_value = (title or "").strip()
    author_value = (author or "").strip()
    return (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(User.device_id == (device_id or "").strip(), PdfBook.book_pdf_name == title_value, PdfBook.book_pdf_author == author_value)
        .order_by(PdfBook.pdf_book_id.desc())
        .first()
    )

def get_book_by_file_checksum_for_device(db: Session, device_id: str, file_checksum: str) -> Optional[PdfBook]:
    checksum_value = (file_checksum or "").strip()
    if not checksum_value:
        return None
    return (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(User.device_id == (device_id or "").strip(), PdfBook.file_checksum == checksum_value)
        .order_by(PdfBook.pdf_book_id.desc())
        .first()
    )


def create_book(
    db: Session,
    title: str,
    author: str,
    content: str,
    description: str = None,
    cover_image: str = None,
    file_checksum: str = None,
    device_id: str = None,
):
    device_value = (device_id or "").strip()
    user = get_or_create_user_by_device(db, device_value)

    existing_book: Optional[PdfBook] = None
    if file_checksum:
        existing_book = get_book_by_file_checksum_for_device(db, device_value, file_checksum)
    if not existing_book:
        existing_book = get_book_by_title_author_for_device(db, device_value, title, author)
    if existing_book:
        if file_checksum:
            existing_book.file_checksum = file_checksum
        if title:
            existing_book.book_pdf_name = title
        if author:
            existing_book.book_pdf_author = author
        if description is not None and description != "":
            existing_book.description = description
        if cover_image:
            existing_book.cover_image = cover_image
        if content and not existing_book.content:
            existing_book.content = content
        db.commit()
        db.refresh(existing_book)
        return existing_book

    db_book = PdfBook(
        book_pdf_name=title,
        book_pdf_author=author,
        file_checksum=file_checksum,
        content=content,
        description=description,
        cover_image=cover_image,
        user_id=user.user_id,
        created_at=datetime.utcnow(),
    )
    db.add(db_book)
    db.commit()
    db.refresh(db_book)
    add_position_entry(db, db_book, user.user_id, 1)
    return db_book


def update_book_progress(db: Session, book_id: int, last_page: int, device_id: str):
    db_book = (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(PdfBook.pdf_book_id == book_id, User.device_id == device_id)
        .first()
    )
    if db_book and db_book.user:
        add_position_entry(db, db_book, db_book.user.user_id, last_page)
        db.refresh(db_book)
    return db_book

def get_audio_position_ms(db: Session, book_id: int, device_id: str) -> Optional[int]:
    db_book = (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(PdfBook.pdf_book_id == book_id, User.device_id == device_id)
        .first()
    )
    if not db_book or not db_book.user:
        return None
    row = (
        db.query(Position)
        .filter(Position.user_id == db_book.user.user_id, Position.pdf_book_id == db_book.pdf_book_id)
        .first()
    )
    if not row:
        return 0
    return int(getattr(row, "audio_position_ms", 0) or 0)


def update_audio_position_ms(db: Session, book_id: int, position_ms: int, device_id: str) -> Optional[int]:
    position_ms = int(position_ms or 0)
    if position_ms < 0:
        position_ms = 0

    db_book = (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(PdfBook.pdf_book_id == book_id, User.device_id == device_id)
        .first()
    )
    if not db_book or not db_book.user:
        return None

    row = (
        db.query(Position)
        .filter(Position.user_id == db_book.user.user_id, Position.pdf_book_id == db_book.pdf_book_id)
        .first()
    )
    if row:
        row.audio_position_ms = position_ms
        row.upload_at = datetime.utcnow()
    else:
        row = Position(
            user_id=db_book.user.user_id,
            pdf_book_id=db_book.pdf_book_id,
            last_position_id=1,
            audio_position_ms=position_ms,
            created_at=datetime.utcnow(),
            upload_at=datetime.utcnow(),
        )
        db.add(row)
    db.commit()
    return position_ms

def update_book(db: Session, book_id: int, device_id: str, title: str = None, author: str = None, description: str = None):
    db_book = (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(PdfBook.pdf_book_id == book_id, User.device_id == device_id)
        .first()
    )
    if db_book:
        if title:
            db_book.book_pdf_name = title
        if author:
            db_book.book_pdf_author = author
        if description:
            db_book.description = description
        db.commit()
        db.refresh(db_book)
    return db_book

def get_audiobook_by_checksum(db: Session, checksum: str) -> Optional[AudioBook]:
    checksum_value = (checksum or "").strip()
    if not checksum_value:
        return None
    return db.query(AudioBook).filter(AudioBook.checksum == checksum_value).first()


def update_book_audio(
    db: Session,
    book_id: int,
    audio_path: str,
    duration: Optional[int] = None,
    checksum: Optional[str] = None,
):
    checksum_value = (checksum or "").strip() if checksum is not None else None
    db_book = (
        db.query(PdfBook)
        .options(selectinload(PdfBook.audio), selectinload(PdfBook.user), selectinload(PdfBook.positions))
        .filter(PdfBook.pdf_book_id == book_id)
        .first()
    )
    if not db_book:
        return None

    file_checksum: Optional[str] = None
    if checksum_value:
        file_checksum = checksum_value
    else:
        try:
            with open(audio_path, "rb") as f:
                file_checksum = compute_file_hash_stream(f)
        except Exception:
            file_checksum = None

    if db_book.audio:
        db_book.audio.file_path = audio_path
        db_book.audio.duration = duration if duration is not None else db_book.audio.duration
        if file_checksum is not None:
            db_book.audio.checksum = file_checksum
    else:
        existing_audio = None
        if file_checksum:
            existing_audio = db.query(AudioBook).filter(AudioBook.checksum == file_checksum).first()
        if not existing_audio:
            existing_audio = db.query(AudioBook).filter(AudioBook.file_path == audio_path).first()

        if not existing_audio:
            existing_audio = AudioBook(
                file_path=audio_path,
                created_at=datetime.utcnow(),
                duration=duration,
                checksum=file_checksum,
            )
            db.add(existing_audio)
            db.flush()
        elif duration is not None and existing_audio.duration is None:
            existing_audio.duration = duration
        if file_checksum is not None and not existing_audio.checksum:
            existing_audio.checksum = file_checksum
        db_book.audio_id = existing_audio.audio_id

    db.commit()
    db.refresh(db_book)
    return db_book

def get_books(db: Session):
    return (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .order_by(PdfBook.pdf_book_id.desc())
        .all()
    )

def get_books_paginated_by_device(db: Session, device_id: str, limit: int, offset: int):
    return (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(User.device_id == device_id)
        .order_by(PdfBook.pdf_book_id.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )

def get_book_by_id(db: Session, book_id: int):
    return (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .filter(PdfBook.pdf_book_id == book_id)
        .first()
    )

def delete_book_for_device(db: Session, book_id: int, device_id: str):
    db_book = (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(PdfBook.pdf_book_id == book_id, User.device_id == device_id)
        .first()
    )
    if db_book:
        db.delete(db_book)
        db.commit()
    return db_book

def search_books_by_device(db: Session, device_id: str, query: str, limit: int, offset: int):
    return (
        db.query(PdfBook)
        .options(selectinload(PdfBook.user), selectinload(PdfBook.audio), selectinload(PdfBook.positions))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(User.device_id == device_id, PdfBook.book_pdf_name.ilike(f"%{query}%"))
        .order_by(PdfBook.pdf_book_id.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )

def count_books_by_device(db: Session, device_id: str) -> int:
    return (
        db.query(func.count(PdfBook.pdf_book_id))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(User.device_id == device_id)
        .scalar()
        or 0
    )

def count_books_search_by_device(db: Session, device_id: str, query: str) -> int:
    return (
        db.query(func.count(PdfBook.pdf_book_id))
        .join(User, PdfBook.user_id == User.user_id)
        .filter(User.device_id == device_id, PdfBook.book_pdf_name.ilike(f"%{query}%"))
        .scalar()
        or 0
    )


def add_position_entry(db: Session, book: PdfBook, user_id: int, new_position: int) -> Position:
    new_position = int(new_position or 1)
    row = (
        db.query(Position)
        .filter(
            Position.user_id == user_id,
            Position.pdf_book_id == book.pdf_book_id,
        )
        .first()
    )
    if row:
        row.last_position_id = new_position
        row.upload_at = datetime.utcnow()
    else:
        row = Position(
            user_id=user_id,
            pdf_book_id=book.pdf_book_id,
            last_position_id=new_position,
            created_at=datetime.utcnow(),
            upload_at=datetime.utcnow(),
        )
        db.add(row)
    db.commit()
    return row
