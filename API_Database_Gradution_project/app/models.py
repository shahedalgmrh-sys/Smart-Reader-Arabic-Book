from datetime import datetime

from sqlalchemy import Column, DateTime, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import relationship

from app.database import Base


class User(Base):
    __tablename__ = "user"

    user_id = Column(Integer, primary_key=True, index=True)
    nick_name = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    device_id = Column(String, nullable=False, unique=True, index=True)

    books = relationship("PdfBook", back_populates="user", cascade="all, delete-orphan")
    positions = relationship("Position", back_populates="user", cascade="all, delete-orphan")


class AudioBook(Base):
    __tablename__ = "audiobook"
    __table_args__ = (
        UniqueConstraint("checksum", name="uq_audiobook_checksum"),
    )

    audio_id = Column(Integer, primary_key=True, index=True)
    file_path = Column(String, nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    duration = Column(Integer, nullable=True)
    checksum = Column(String, nullable=True, index=True)

    books = relationship("PdfBook", back_populates="audio")


class PdfBook(Base):
    __tablename__ = "pdfbook"
    __table_args__ = (
        UniqueConstraint("user_id", "file_checksum", name="uq_pdfbook_user_file_checksum"),
    )

    pdf_book_id = Column(Integer, primary_key=True, index=True)
    audio_id = Column(Integer, ForeignKey("audiobook.audio_id"), nullable=True, index=True)
    user_id = Column(Integer, ForeignKey("user.user_id"), nullable=False, index=True)
    book_pdf_name = Column(String, nullable=False)
    book_pdf_author = Column(String, nullable=True)
    cover_image = Column(String, nullable=True)
    file_checksum = Column(String, nullable=True, index=True)
    content = Column(Text, nullable=True)
    description = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="books")
    audio = relationship("AudioBook", back_populates="books")
    positions = relationship("Position", back_populates="book", cascade="all, delete-orphan")

    @property
    def id(self):
        return self.pdf_book_id

    @property
    def title(self):
        return self.book_pdf_name

    @title.setter
    def title(self, value):
        self.book_pdf_name = value

    @property
    def author(self):
        return self.book_pdf_author

    @author.setter
    def author(self, value):
        self.book_pdf_author = value

    @property
    def device_id(self):
        return self.user.device_id if self.user else None

    @property
    def audio_path(self):
        return self.audio.file_path if self.audio else None

    @property
    def last_page(self):
        if not self.positions:
            return 1
        latest = max(self.positions, key=lambda p: p.upload_at or p.created_at or datetime.min)
        return int(latest.last_position_id or 1)


class Position(Base):
    __tablename__ = "position"

    user_id = Column(Integer, ForeignKey("user.user_id"), primary_key=True, nullable=False, index=True)
    pdf_book_id = Column(Integer, ForeignKey("pdfbook.pdf_book_id"), primary_key=True, nullable=False, index=True)
    last_position_id = Column(Integer, nullable=False, default=1)
    audio_position_ms = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    upload_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="positions")
    book = relationship("PdfBook", back_populates="positions")


Index("ix_pdfbook_user_name_author", PdfBook.user_id, PdfBook.book_pdf_name, PdfBook.book_pdf_author)
Index("ix_position_user_book", Position.user_id, Position.pdf_book_id)


Book = PdfBook
