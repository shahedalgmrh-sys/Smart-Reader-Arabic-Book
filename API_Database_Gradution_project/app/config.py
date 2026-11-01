import os

class Settings:
    PROJECT_NAME = "Book Audio Generator"
    
    # Base Directory
    BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    
    # AI Configuration
    TTS_ENGINE = os.getenv("TTS_ENGINE", "local")
    
    # Repo Path (Prioritize env var, else default to project root)
    TTS_ARABIC_PYTORCH_PATH = os.getenv("TTS_ARABIC_PYTORCH_PATH", 
                                        os.path.join(BASE_DIR, "third_party", "tts-arabic-pytorch"))
    
    # Model Path
    TTS_ACOUSTIC_CKPT = os.getenv("TTS_ACOUSTIC_CKPT", 
                                  os.path.join(BASE_DIR, "app", "models", "states_3000.pth"))
    
    # Storage Paths
    AUDIO_DIR = os.getenv("AUDIO_DIR", os.path.join(BASE_DIR, "generated_audio"))
    COVER_DIR = os.getenv("COVER_DIR", os.path.join(BASE_DIR, "uploaded_covers"))
    DEFAULT_COVER_PATH = os.getenv("DEFAULT_COVER_PATH", os.path.join(BASE_DIR, "default_cover.jpg"))
    BOOK_DIR = os.getenv("BOOK_DIR", os.path.join(BASE_DIR, "uploaded_books"))
    ARCHIVE_DIR = os.getenv("ARCHIVE_DIR", os.path.join(BASE_DIR, "archive"))
    ARCHIVE_BOOK_DIR = os.getenv("ARCHIVE_BOOK_DIR", os.path.join(ARCHIVE_DIR, "books"))
    ARCHIVE_AUDIO_DIR = os.getenv("ARCHIVE_AUDIO_DIR", os.path.join(ARCHIVE_DIR, "audio"))
    RESTORE_TOKEN_SECRET = os.getenv("RESTORE_TOKEN_SECRET", "dev-restore-secret")

settings = Settings()
