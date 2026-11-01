from app import crud

def test_filter_by_device_id(db):
    # Create books for two devices
    b1 = crud.create_book(db, "A", "Auth1", "Content1", description=None, cover_image=None, device_id="device_1")
    b2 = crud.create_book(db, "B", "Auth2", "Content2", description=None, cover_image=None, device_id="device_2")
    b3 = crud.create_book(db, "C", "Auth3", "Content3", description=None, cover_image=None, device_id="device_1")

    # Query for device_1
    list1 = crud.get_books_paginated_by_device(db, device_id="device_1", limit=10, offset=0)
    assert {b.id for b in list1} == {b1.id, b3.id}

    # Search for device_1
    search1 = crud.search_books_by_device(db, device_id="device_1", query="C", limit=10, offset=0)
    assert len(search1) == 1 and search1[0].id == b3.id

    # Count for device_2
    count2 = crud.count_books_by_device(db, device_id="device_2")
    assert count2 == 1

    # Delete for device mismatch should not delete
    deleted = crud.delete_book_for_device(db, book_id=b1.id, device_id="device_2")
    assert deleted is None
    # Delete for owner
    deleted_ok = crud.delete_book_for_device(db, book_id=b1.id, device_id="device_1")
    assert deleted_ok is not None
