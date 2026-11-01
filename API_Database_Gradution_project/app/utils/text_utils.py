import hashlib
import re
from typing import BinaryIO


def clean_extracted_text(raw: str) -> str:
    lines = raw.splitlines()
    cleaned_lines = []
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        if re.fullmatch(r"[0-9\u0660-\u0669]+", stripped):
            continue
        lower = stripped.lower()
        if re.fullmatch(r"page\s+[0-9]+", lower):
            continue
        if re.fullmatch(r"صفحة\s+[0-9\u0660-\u0669]+", stripped):
            continue
        no_digits = re.sub(r"[0-9\u0660-\u0669]+", "", stripped)
        no_digits = re.sub(r"\s+", " ", no_digits).strip()
        if no_digits:
            cleaned_lines.append(no_digits)
    result = "\n".join(cleaned_lines)
    result = re.sub(r"\n{3,}", "\n\n", result)
    return result.strip()


_ARABIC_DIACRITICS_RE = re.compile(r"[\u064B-\u0652\u0670]")
_ARABIC_TATWEEL_RE = re.compile(r"\u0640+")
_ARABIC_LETTER_NORMALIZATION = str.maketrans(
    {
        "أ": "ا",
        "إ": "ا",
        "آ": "ا",
        "ٱ": "ا",
        "ى": "ي",
        "ئ": "ي",
        "ؤ": "و",
        "ة": "ه",
    }
)


def _normalize_arabic_for_hash(text: str) -> str:
    text = _ARABIC_DIACRITICS_RE.sub("", text)
    text = _ARABIC_TATWEEL_RE.sub("", text)
    return text.translate(_ARABIC_LETTER_NORMALIZATION)


def _normalized_text_for_hash_v1(text: str) -> str:
    lowered = text.strip().lower()
    lowered = re.sub(r"\s+", " ", lowered)
    lowered = re.sub(r"[^\w\s\u0600-\u06FF]", "", lowered)
    return lowered


def _normalized_text_for_hash_v2(text: str) -> str:
    lowered = text.strip().lower()
    lowered = _normalize_arabic_for_hash(lowered)
    lowered = re.sub(r"[٠-٩]", lambda m: str("٠١٢٣٤٥٦٧٨٩".index(m.group(0))), lowered)
    lowered = re.sub(r"\s+", " ", lowered)
    lowered = re.sub(r"[^\w\s\u0600-\u06FF]", " ", lowered)
    lowered = re.sub(r"\s+", " ", lowered).strip()
    return lowered


def compute_text_hash(text: str, version: str = "v2") -> str:
    if version == "v1":
        normalized = _normalized_text_for_hash_v1(text)
    else:
        normalized = _normalized_text_for_hash_v2(text)
    if not normalized:
        normalized = text.strip()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def compute_text_hashes(text: str) -> list[str]:
    v2 = compute_text_hash(text, version="v2")
    v1 = compute_text_hash(text, version="v1")
    if v2 == v1:
        return [v2]
    return [v2, v1]


def compute_file_hash_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def compute_file_hash_stream(stream: BinaryIO, chunk_size: int = 1024 * 1024) -> str:
    h = hashlib.sha256()
    while True:
        chunk = stream.read(chunk_size)
        if not chunk:
            break
        h.update(chunk)
    return h.hexdigest()


def clean_text_for_tts(text: str) -> str:
    """
    Normalizes text for TTS processing:
    - Removes HTML tags
    - Unifies whitespace
    - Removes non-Arabic/English alphanumeric characters (optional)
    - Removes noisy symbols and punctuation
    """
    # 1. Remove HTML tags
    text = re.sub(r'<[^>]+>', ' ', text)
    
    # 2. Basic cleanup
    text = text.replace("\n", " ").replace("\r", " ").replace("\t", " ")
    text = text.replace("\u200f", " ").replace("\u200e", " ")
    
    # 3. Remove numbers (optional, based on ai_service logic)
    text = re.sub(r"[0-9\u0660-\u0669]+", " ", text)
    
    # 4. Remove noisy symbols AND punctuation
    # Include Arabic comma (،), Question mark (؟), Semicolon (؛)
    text = re.sub(r"[|@#$%^&*_=~`\-.,?!:;،؟؛]+", " ", text)
    
    # 5. Normalize whitespace
    text = re.sub(r"\s+", " ", text).strip()
    return text

