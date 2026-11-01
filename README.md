# Smart Reader Arabic Book 📱📚

A full-stack **Arabic audio-book reader**: users upload any book (PDF / DOCX / EPUB), read it in a clean Arabic-first UI, and the backend converts it to speech using a **self-trained Arabic Tacotron2 TTS model**.

## ✨ Features

| | |
|---|---|
| 📖 **Book library** | Upload PDF/DOCX/EPUB/MOBI with cover image, paginated listing, title search, device-scoped ownership |
| 🔊 **Server-side TTS** | Background audio generation with live progress tracking (0–100 %), streamed WAV/MP3 playback |
| 🧠 **Custom Arabic voice** | Tacotron2 fine-tuned on Arabic speech data (HiFi-GAN vocoder), built-in text cleaning & Arabic phonemisation |
| 📄 **Checksum deduplication** | SHA-256 content fingerprint per device — re-uploading the same book never duplicates it |
| ⏯️ **Resume everywhere** | Reading position and audio playback position (ms) synced per user & book |
| 🌍 **Arabic-first UI** | Full RTL support with optional English LTR mode, dark theme, accent colours |
| 🎙️ **Extras** | Immersive reader, mic recording utilities, retry-safe networking, glassmorphism AI-assistant UI |

## 🏗️ Architecture

```
┌─────────────────────┐     HTTP/JSON      ┌──────────────────────────────┐
│  Android app        │  ────────────────► │  FastAPI backend             │
│  Kotlin + Compose   │   Retrofit/OkHttp  │                              │
│                     │                    │  ┌────────────────────────┐  │
│  • Library & search │  ◄──────────────── │  │ PostgreSQL (SQLAlchemy)│  │
│  • Immersive reader │   pages, audio,    │  └────────────────────────┘  │
│  • Audio player     │   progress events  │                              │
└─────────────────────┘                    │  ┌────────────────────────┐  │
                                           │  │ TTS engine (PyTorch)   │  │
┌─────────────────────┐                    │  │ Tacotron2 + HiFi-GAN   │  │
│  Training pipeline  │  ─── checkpoints ──►  └────────────────────────┘  │
│  Python scripts     │                    └──────────────────────────────┘
└─────────────────────┘
```

## 🛠️ Tech stack

| Layer | Technologies |
|---|---|
| **Android** | Kotlin, Jetpack Compose (Material 3), Retrofit + OkHttp, Coil, DataStore, Navigation Compose |
| **Backend** | Python, FastAPI, SQLAlchemy, PostgreSQL, Uvicorn, background tasks, GZip |
| **AI / TTS** | PyTorch, torchaudio, librosa, [tts-arabic-pytorch](https://github.com/nipponjo/tts-arabic-pytorch) (Tacotron2), HiFi-GAN |
| **Ops** | Docker + docker-compose, pytest API test-suite, `.env` configuration |

## 📂 Project structure

```
├── API_Database_Gradution_project/   # FastAPI backend
│   ├── app/                          # main.py, routes, CRUD, models, schemas, services
│   │   ├── services/                 # ai_service.py — Arabic TTS pipeline
│   │   └── models/                   # pretrained checkpoint (not in repo)
│   ├── tests/                        # pytest API tests
│   ├── setup_database.py             # PostgreSQL helpers (stored functions)
│   ├── Dockerfile / docker-compose.yml
├── ReaderARBICBook/                  # Android application (Gradle 8.5, Kotlin 1.9)
│   └── app/src/main/java/com/example/readerarbicbook/
│       ├── ui/screens/               # Dashboard, Library, AddBook, ImmersiveReader…
│       ├── network/                  # Retrofit API definitions
│       ├── data/ + viewmodel/        # repositories, DataStore preferences, state
│       └── audio/ utils/             # recording + network helpers
├── scripts/                          # Training pipeline
│   ├── prepare_dataset.py            # ASS subtitles + MP3 → sliced WAV dataset
│   ├── train.py                      # auto-resuming training wrapper
│   └── generate_sample.py            # checkpoint inference / sample generation
├── docs/                             # Arabic project documentation
└── training_data/albaraa_config.yaml # Hyper-parameters (AdamW, 5000 epochs, batch 24)
```

## 🚀 Getting started

### 1. Backend

```bash
cd API_Database_Gradution_project
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt

# PostgreSQL connection required (see .env.example)
copy .env.example .env   # then edit DATABASE_URL

# Get the TTS engine + checkpoint (see "Model notes" below)
git clone https://github.com/nipponjo/tts-arabic-pytorch.git third_party/tts-arabic-pytorch
# place your trained checkpoint (states_….pth) in app/models/

python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
# Swagger UI: http://localhost:8000/docs
```

### 2. Android app

1. Open `ReaderARBICBook/` in **Android Studio**.
2. Set `BASE_URL` in `app/build.gradle.kts` to your server address
   (emulator → `http://10.0.2.2:8000/`, real device → your LAN IP).
3. Run the `app` configuration.

### 3. Training your own voice (optional)

```bash
pip install torch librosa soundfile

# Build the dataset from subtitles + audio
python scripts/prepare_dataset.py

# Train with auto-resume from the latest checkpoint
python scripts/train.py

# Generate a test sample
python scripts/generate_sample.py --text "بسم الله الرحمن الرحيم" --out sample.wav
```

## 🔌 API overview

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/health` | Server + database health check |
| `POST` | `/books` | Upload book (multipart + cover) with device identity |
| `GET` / `GET /count` | `/books` | Paginated library, title search, per device |
| `GET/PUT/DELETE` | `/books/{id}` | Book detail / update / delete |
| `POST` | `/books/{id}/generate_audio` | Start background TTS conversion |
| `GET` | `/books/{id}/progress` | TTS progress (0–100 %) |
| `GET` | `/books/{id}/audio` | Stream generated audio |
| `PUT/GET` | `/books/{id}/audio_position` | Audio resume position |
| `GET` | `/books/{id}/cover` | Cover image (with fallback) |

> Full endpoint docs in `docs/PROJECT_DOCUMENTATION.md`

## 🧠 Model notes

- Arabic Tacotron2 (phonemised text input) + HiFi-GAN vocoder.
- Dataset built from ~260 aligned audio/text segments (single speaker).
- Training caps at 5000 epochs, AdamW (lr 1e-3), batch 24 — checkpoints saved every 100 steps.
- Binary checkpoints (`.pth`) are **not hosted in this repo** — contact me if you want the trained model.
- Third-party inference lib is excluded (`third_party/`); clone it via the URL above.

## 🔭 Roadmap / known limitations

- [x] Device-based library scoping & checksum dedup
- [x] Async TTS generation with progress reporting
- [ ] Proper user accounts (auth) instead of device-ID identity
- [ ] PostgreSQL migration hardening with Alembic
- [ ] Faster vocoder path & multi-quality audio options
- [ ] Archive/restore endpoints (designed, in progress)

## 📜 License

Academic graduation project — code provided as-is for educational purposes.
