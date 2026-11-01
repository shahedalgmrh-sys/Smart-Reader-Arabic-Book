from app import crud
import os
import tempfile


def test_read_books_with_header_and_query_fallback(client, db):
    device_hdr = "hdr_123"
    device_q = "qr_456"
    # seed data
    crud.create_book(db, "H1", "A", "C", None, None, device_id=device_hdr)
    crud.create_book(db, "Q1", "A", "C", None, None, device_id=device_q)

    r_hdr = client.get("/books", headers={"X-Device-ID": device_hdr})
    assert r_hdr.status_code == 200
    items_hdr = r_hdr.json()
    assert all(it["author"] == "A" for it in items_hdr)

    r_q = client.get("/books", params={"android_id": device_q})
    assert r_q.status_code == 200
    items_q = r_q.json()
    assert all(it["author"] == "A" for it in items_q)


def test_read_books_missing_device_id_returns_400(client):
    r = client.get("/books")
    assert r.status_code == 400
    assert "Device ID" in r.json()["detail"]


def test_count_endpoint_header_and_query(client):
    device = "count_dev"
    headers = {"X-Device-ID": device}
    client.post("/books/json", json={"title": "C1", "author": "A", "content": "X"}, headers=headers)
    client.post("/books/json", json={"title": "C2", "author": "A", "content": "X"}, headers=headers)

    r_hdr = client.get("/books/count", headers=headers)
    assert r_hdr.status_code == 200
    assert isinstance(r_hdr.json()["count"], int)

    r_q = client.get("/books/count", params={"android_id": device})
    assert r_q.status_code == 200
    assert isinstance(r_q.json()["count"], int)


def test_book_detail_update_progress_and_audio_with_query_android_id(client, db):
    device_id = "android_query_1"
    book = crud.create_book(db, "T", "A", "C", None, None, device_id=device_id)

    r_book = client.get(f"/books/{book.id}", params={"android_id": device_id})
    assert r_book.status_code == 200
    assert r_book.json()["id"] == book.id

    r_progress_update = client.put(
        f"/books/{book.id}/progress",
        params={"android_id": device_id, "last_page": 5},
    )
    assert r_progress_update.status_code == 200
    assert r_progress_update.json()["last_page"] == 5

    r_audio_pos_update = client.put(
        f"/books/{book.id}/audio_position",
        params={"android_id": device_id, "position_ms": 12345},
    )
    assert r_audio_pos_update.status_code == 200
    assert r_audio_pos_update.json()["position_ms"] == 12345

    r_audio_pos_get = client.get(
        f"/books/{book.id}/audio_position",
        params={"android_id": device_id},
    )
    assert r_audio_pos_get.status_code == 200
    assert r_audio_pos_get.json()["position_ms"] == 12345

    wav_bytes = b"RIFF" + (b"\x00" * 4) + b"WAVE" + (b"\x00" * 256)
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".wav")
    try:
        tmp.write(wav_bytes)
        tmp.flush()
        tmp.close()

        crud.update_book_audio(db, book.id, tmp.name)

        r_audio = client.get(f"/books/{book.id}/audio", params={"android_id": device_id})
        assert r_audio.status_code == 200
        assert r_audio.content.startswith(b"RIFF")
        assert r_audio.headers.get("content-type") == "audio/wav"
    finally:
        try:
            os.unlink(tmp.name)
        except Exception:
            pass


def test_cover_with_query_android_id(client, db):
    device_id = "android_query_2"
    book = crud.create_book(db, "T2", "A2", "C2", None, None, device_id=device_id)

    img = tempfile.NamedTemporaryFile(delete=False, suffix=".png")
    try:
        img.write(b"\x89PNG\r\n\x1a\n" + (b"\x00" * 256))
        img.flush()
        img.close()

        db_book = crud.get_book_by_id(db, book.id)
        db_book.cover_image = img.name
        db.commit()

        r_cover = client.get(f"/books/{book.id}/cover", params={"android_id": device_id})
        assert r_cover.status_code == 200
        assert r_cover.content.startswith(b"\x89PNG\r\n\x1a\n")
        assert r_cover.headers.get("content-type") == "image/png"
    finally:
        try:
            os.unlink(img.name)
        except Exception:
            pass
