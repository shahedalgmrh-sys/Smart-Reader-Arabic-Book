from pydantic import BaseModel, Field, ConfigDict
from typing import Optional

class BookCreatePayload(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    author: str = Field(min_length=1, max_length=200)
    description: Optional[str] = None
    content: str = Field(min_length=1)

class BookUpdate(BaseModel):
    title: Optional[str] = Field(None, min_length=1, max_length=200)
    author: Optional[str] = Field(None, min_length=1, max_length=200)
    description: Optional[str] = None

class BookResponse(BaseModel):
    id: int
    title: str
    author: str
    description: Optional[str] = None
    cover_image: Optional[str] = None
    audio_path: Optional[str] = None
    device_id: str
    last_page: int = 1
    model_config = ConfigDict(from_attributes=True)

class BookDetail(BookResponse):
    content: str
