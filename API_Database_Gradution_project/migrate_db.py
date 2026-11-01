from sqlalchemy import text
from sqlalchemy.exc import OperationalError
from app.database import engine


def _safe_add_column(conn, ddl: str):
    try:
        conn.execute(text(ddl))
    except OperationalError as e:
        msg = str(e).lower()
        if "duplicate column" in msg or "already exists" in msg:
            return
        raise


def migrate():
    with engine.connect() as conn:
        print("Applying migrations...")
        _safe_add_column(conn, "ALTER TABLE books ADD COLUMN description TEXT")
        _safe_add_column(conn, "ALTER TABLE books ADD COLUMN cover_image VARCHAR")
        _safe_add_column(conn, "ALTER TABLE books ADD COLUMN audio_path VARCHAR")
        _safe_add_column(conn, "ALTER TABLE books ADD COLUMN device_id VARCHAR NOT NULL DEFAULT ''")
        _safe_add_column(conn, "ALTER TABLE books ADD COLUMN content_hash VARCHAR")
        conn.execute(text("CREATE INDEX IF NOT EXISTS idx_books_device_id ON books(device_id)"))
        conn.execute(text("CREATE INDEX IF NOT EXISTS ix_books_content_hash ON books(content_hash)"))
        conn.commit()
        print("Migrations applied successfully.")

if __name__ == "__main__":
    migrate()
