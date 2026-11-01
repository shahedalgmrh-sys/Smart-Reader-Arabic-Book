# API Integration Documentation

## Authentication
None required. Device identification via `android_id` query/body.

## Endpoints

### POST /books
Multipart form to create a book.
Parts:
- title: text/plain
- author: text/plain
- description: text/plain (optional)
- file: application/pdf | application/vnd.openxmlformats-officedocument.wordprocessingml.document | application/epub+zip | text/plain
- cover_image: image/jpeg | image/png (optional)
- android_id: text/plain
Response: JSON BookResponse

### POST /books/json
Create a book from direct text.
Body:
```
{
  "title": "...",
  "author": "...",
  "content": "...",
  "description": "...",
  "android_id": "..."
}
```
Response: JSON BookResponse

### GET /books
Query:
- limit: int
- offset: int
- q: string (optional)
- android_id: string
Response: JSON List<BookSummary>

### GET /books/{id}
Query:
- android_id: string
Response: JSON BookResponse

### POST /books/{id}/generate_audio
Query:
- android_id: string
Response: JSON AudioResponse

### GET /books/{id}/audio
Query:
- android_id: string
Response: audio/mp3 stream

### DELETE /books/{id}
Query:
- android_id: string
Response: 200 OK

## Notes
- Use HTTPS in release build.
- Client records WAV locally under `recordings/` and uploads content via JSON or file.

