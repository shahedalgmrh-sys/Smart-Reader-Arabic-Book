def test_post_books_json_with_query_android_id_without_header(client):
    device_id = "form_device_1"
    payload = {"title": "FormBook", "author": "Tester", "content": "Hello"}
    r = client.post("/books/json", json=payload, params={"android_id": device_id})
    assert r.status_code == 200, r.text
    book = r.json()
    assert book["title"] == "FormBook"
    assert book["device_id"] == device_id


def test_post_books_json_with_header_android_id(client):
    device_id = "header_device_1"
    payload = {"title": "HeaderBook", "author": "Tester", "content": "Hello"}
    headers = {"X-Device-ID": device_id}
    r = client.post("/books/json", json=payload, headers=headers)
    assert r.status_code == 200, r.text
    book = r.json()
    assert book["title"] == "HeaderBook"
    assert book["device_id"] == device_id
