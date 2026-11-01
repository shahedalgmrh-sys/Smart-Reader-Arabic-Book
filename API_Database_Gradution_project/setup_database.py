import os

from dotenv import load_dotenv


def ensure_db_functions() -> None:
    from sqlalchemy import text

    from app.database import engine

    statements: list[str] = []

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_get_or_create_user(p_device_id text, p_nick_name text DEFAULT NULL)
RETURNS integer
LANGUAGE sql
AS $$
    INSERT INTO "user" (device_id, nick_name, created_at)
    VALUES (
        btrim(COALESCE(p_device_id, '')),
        COALESCE(
            p_nick_name,
            CASE
                WHEN btrim(COALESCE(p_device_id, '')) = '' THEN 'anonymous_user'
                ELSE 'user_' || btrim(p_device_id)
            END
        ),
        now()
    )
    ON CONFLICT (device_id) DO UPDATE
    SET device_id = EXCLUDED.device_id
    RETURNING user_id;
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_upsert_position(p_device_id text, p_book_id integer, p_last_page integer)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_user_id integer;
BEGIN
    v_user_id := public.fn_get_or_create_user(p_device_id, NULL);

    INSERT INTO position (user_id, pdf_book_id, last_position_id, created_at, upload_at)
    VALUES (v_user_id, p_book_id, COALESCE(p_last_page, 1), now(), now())
    ON CONFLICT (user_id, pdf_book_id) DO UPDATE
    SET last_position_id = EXCLUDED.last_position_id,
        upload_at = now();
END;
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_create_pdfbook(
    p_device_id text,
    p_title text,
    p_author text,
    p_content text,
    p_description text DEFAULT NULL,
    p_cover_image text DEFAULT NULL
)
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    v_user_id integer;
    v_book_id integer;
BEGIN
    v_user_id := public.fn_get_or_create_user(p_device_id, NULL);

    SELECT pb.pdf_book_id
    INTO v_book_id
    FROM pdfbook pb
    WHERE pb.user_id = v_user_id
      AND pb.book_pdf_name = btrim(COALESCE(p_title, ''))
      AND pb.book_pdf_author = btrim(COALESCE(p_author, ''))
    ORDER BY pb.pdf_book_id DESC
    LIMIT 1;

    IF v_book_id IS NOT NULL THEN
        UPDATE pdfbook
        SET description = COALESCE(description, p_description),
            cover_image = COALESCE(cover_image, p_cover_image),
            content = COALESCE(content, p_content)
        WHERE pdf_book_id = v_book_id;

        PERFORM public.fn_upsert_position(p_device_id, v_book_id, 1);
        RETURN v_book_id;
    END IF;

    INSERT INTO pdfbook (audio_id, user_id, book_pdf_name, book_pdf_author, cover_image, content, description, created_at)
    VALUES (
        NULL,
        v_user_id,
        btrim(COALESCE(p_title, '')),
        btrim(COALESCE(p_author, '')),
        p_cover_image,
        p_content,
        p_description,
        now()
    )
    RETURNING pdf_book_id INTO v_book_id;

    PERFORM public.fn_upsert_position(p_device_id, v_book_id, 1);
    RETURN v_book_id;
END;
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_delete_pdfbook_for_device(p_device_id text, p_book_id integer)
RETURNS boolean
LANGUAGE plpgsql
AS $$
DECLARE
    v_user_id integer;
    v_deleted integer;
BEGIN
    SELECT u.user_id
    INTO v_user_id
    FROM "user" u
    WHERE u.device_id = btrim(COALESCE(p_device_id, ''))
    LIMIT 1;

    IF v_user_id IS NULL THEN
        RETURN false;
    END IF;

    DELETE FROM position p
    WHERE p.user_id = v_user_id
      AND p.pdf_book_id = p_book_id;

    DELETE FROM pdfbook pb
    WHERE pb.pdf_book_id = p_book_id
      AND pb.user_id = v_user_id
    RETURNING 1 INTO v_deleted;

    RETURN v_deleted IS NOT NULL;
END;
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_count_books_by_device(p_device_id text)
RETURNS integer
LANGUAGE sql
AS $$
    SELECT COALESCE(count(*), 0)::integer
    FROM pdfbook pb
    JOIN "user" u ON u.user_id = pb.user_id
    WHERE u.device_id = btrim(COALESCE(p_device_id, ''));
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_get_audiobook_by_checksum(p_checksum text)
RETURNS TABLE (
    audio_id integer,
    file_path text,
    created_at timestamp,
    duration integer,
    checksum text
)
LANGUAGE sql
AS $$
    SELECT
        a.audio_id,
        a.file_path::text,
        a.created_at,
        a.duration,
        a.checksum::text
    FROM audiobook a
    WHERE a.checksum = btrim(COALESCE(p_checksum, ''))
    ORDER BY a.audio_id DESC
    LIMIT 1;
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_create_audiobook(p_file_path text, p_duration integer, p_checksum text)
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    v_audio_id integer;
    v_checksum text;
BEGIN
    v_checksum := NULLIF(btrim(COALESCE(p_checksum, '')), '');
    IF v_checksum IS NOT NULL THEN
        SELECT a.audio_id
        INTO v_audio_id
        FROM audiobook a
        WHERE a.checksum = v_checksum
        ORDER BY a.audio_id DESC
        LIMIT 1;
        IF v_audio_id IS NOT NULL THEN
            RETURN v_audio_id;
        END IF;
    END IF;

    INSERT INTO audiobook (file_path, created_at, duration, checksum)
    VALUES (p_file_path, now(), p_duration, v_checksum)
    RETURNING audio_id INTO v_audio_id;
    RETURN v_audio_id;
END;
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_search_books_by_device(
    p_device_id text,
    p_query text,
    p_limit integer DEFAULT 50,
    p_offset integer DEFAULT 0
)
RETURNS TABLE (
    pdf_book_id integer,
    book_pdf_name text,
    book_pdf_author text,
    cover_image text,
    description text,
    audio_id integer,
    created_at timestamp,
    last_page integer
)
LANGUAGE sql
AS $$
    SELECT
        pb.pdf_book_id,
        pb.book_pdf_name::text,
        pb.book_pdf_author::text,
        pb.cover_image::text,
        pb.description::text,
        pb.audio_id,
        pb.created_at,
        COALESCE(p.last_position_id, 1)::integer AS last_page
    FROM pdfbook pb
    JOIN "user" u ON u.user_id = pb.user_id
    LEFT JOIN position p ON p.user_id = u.user_id AND p.pdf_book_id = pb.pdf_book_id
    WHERE u.device_id = btrim(COALESCE(p_device_id, ''))
      AND pb.book_pdf_name ILIKE ('%' || COALESCE(p_query, '') || '%')
    ORDER BY pb.pdf_book_id DESC
    OFFSET GREATEST(COALESCE(p_offset, 0), 0)
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);
$$;
"""
    )

    statements.append(
        """
CREATE OR REPLACE FUNCTION public.fn_list_books_by_device(
    p_device_id text,
    p_limit integer DEFAULT 50,
    p_offset integer DEFAULT 0
)
RETURNS TABLE (
    pdf_book_id integer,
    book_pdf_name text,
    book_pdf_author text,
    cover_image text,
    description text,
    audio_id integer,
    created_at timestamp,
    last_page integer
)
LANGUAGE sql
AS $$
    SELECT
        pb.pdf_book_id,
        pb.book_pdf_name::text,
        pb.book_pdf_author::text,
        pb.cover_image::text,
        pb.description::text,
        pb.audio_id,
        pb.created_at,
        COALESCE(p.last_position_id, 1)::integer AS last_page
    FROM pdfbook pb
    JOIN "user" u ON u.user_id = pb.user_id
    LEFT JOIN position p ON p.user_id = u.user_id AND p.pdf_book_id = pb.pdf_book_id
    WHERE u.device_id = btrim(COALESCE(p_device_id, ''))
    ORDER BY pb.pdf_book_id DESC
    OFFSET GREATEST(COALESCE(p_offset, 0), 0)
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);
$$;
"""
    )

    with engine.begin() as conn:
        for stmt in statements:
            conn.execute(text(stmt))


def ensure_database_exists() -> None:
    import psycopg2
    from psycopg2 import sql

    host = os.getenv("PGHOST", "localhost")
    port = int(os.getenv("PGPORT", "5432"))
    user = os.getenv("PGUSER", "odoo17")
    password = os.getenv("PGPASSWORD", "odoo17")
    database_name = os.getenv("PGDATABASE", "book_reader")
    admin_db = os.getenv("PGADMIN_DB", "postgres")

    admin_user = os.getenv("PGADMIN_USER")
    admin_password = os.getenv("PGADMIN_PASSWORD")

    last_error: Exception | None = None
    candidates: list[tuple[str, str | None]] = []
    if admin_user:
        candidates.append((admin_user, admin_password))
    candidates.append((user, password))
    candidates.append(("postgres", admin_password))
    candidates.append(("postgres", None))

    conn = None
    for cand_user, cand_password in candidates:
        try:
            conn = psycopg2.connect(
                dbname=admin_db,
                user=cand_user,
                password=cand_password,
                host=host,
                port=port,
            )
            break
        except Exception as e:
            last_error = e
            conn = None
            continue

    if conn is None:
        raise RuntimeError(f"Failed to connect to PostgreSQL admin database '{admin_db}'") from last_error

    conn.autocommit = True
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT 1 FROM pg_roles WHERE rolname = %s", (user,))
            role_exists = cur.fetchone() is not None
            if not role_exists:
                cur.execute(
                    sql.SQL("CREATE ROLE {} LOGIN PASSWORD %s").format(sql.Identifier(user)),
                    (password,),
                )
            cur.execute("SELECT 1 FROM pg_database WHERE datname = %s", (database_name,))
            exists = cur.fetchone() is not None
            if not exists:
                cur.execute(
                    sql.SQL("CREATE DATABASE {} OWNER {}").format(
                        sql.Identifier(database_name),
                        sql.Identifier(user),
                    )
                )
    finally:
        conn.close()


def ensure_database_url() -> str:
    database_url = os.getenv("DATABASE_URL")
    if database_url:
        return database_url

    host = os.getenv("PGHOST", "localhost")
    port = int(os.getenv("PGPORT", "5432"))
    user = os.getenv("PGUSER", "odoo17")
    password = os.getenv("PGPASSWORD", "odoo17")
    database_name = os.getenv("PGDATABASE", "book_reader")

    database_url = f"postgresql+psycopg2://{user}:{password}@{host}:{port}/{database_name}"
    os.environ["DATABASE_URL"] = database_url
    return database_url


def main() -> None:
    load_dotenv()
    ensure_database_exists()
    database_url = ensure_database_url()
    from app.database import Base, engine
    from app import models  # noqa: F401

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    ensure_db_functions()
    print(f"Database ready: {database_url}")


if __name__ == "__main__":
    main()
