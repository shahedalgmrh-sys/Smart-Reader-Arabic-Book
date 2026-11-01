import os

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL")
if not DATABASE_URL:
    raise RuntimeError("DATABASE_URL is not set. Create a .env file with DATABASE_URL for PostgreSQL.")

connect_args = {}
if DATABASE_URL.startswith("postgres"):
    connect_args = {"connect_timeout": int(os.getenv("DB_CONNECT_TIMEOUT", "5"))}

engine = create_engine(DATABASE_URL, pool_pre_ping=True, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False, expire_on_commit=False)

Base = declarative_base()


def init_db() -> None:
    from app import models  # noqa: F401

    Base.metadata.create_all(bind=engine)
    if DATABASE_URL.startswith("postgres"):
        with engine.begin() as conn:
            pdf_has_file_checksum = conn.exec_driver_sql(
                """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'pdfbook'
                  AND column_name = 'file_checksum'
                LIMIT 1
                """
            ).fetchone()
            if not pdf_has_file_checksum:
                conn.exec_driver_sql("ALTER TABLE pdfbook ADD COLUMN file_checksum VARCHAR")

            position_has_audio_pos = conn.exec_driver_sql(
                """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'position'
                  AND column_name = 'audio_position_ms'
                LIMIT 1
                """
            ).fetchone()
            if not position_has_audio_pos:
                conn.exec_driver_sql("ALTER TABLE position ADD COLUMN audio_position_ms INTEGER NOT NULL DEFAULT 0")

            conn.exec_driver_sql("CREATE INDEX IF NOT EXISTS ix_pdfbook_file_checksum ON pdfbook (file_checksum)")
            conn.exec_driver_sql(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_audiobook_checksum_not_blank ON audiobook (checksum) WHERE checksum IS NOT NULL AND btrim(checksum) <> ''"
            )
            conn.exec_driver_sql(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_pdfbook_user_file_checksum_not_blank ON pdfbook (user_id, file_checksum) WHERE file_checksum IS NOT NULL AND btrim(file_checksum) <> ''"
            )
