import os
import sys
import logging
from typing import List, Optional
from app.config import settings

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# هذا الملف مسؤول عن "خدمة تحويل النص إلى صوت" ضمن الـ Backend.
#
# الفكرة العامة:
# - توجد دوال مساعدة لتقسيم النص الطويل إلى مقاطع أقصر.
# - توجد دوال لاكتشاف مسارات المشروع ومجلد النماذج (models).
# - توجد دوال لتفعيل/تعطيل محرّك تحويل نص→صوت محلي يعتمد على PyTorch.
# - عند تفعيل التشغيل المحلي، يتم استخدام كود خارجي (repo: tts-arabic-pytorch)
#   لتوليد موجة صوتية (Waveform) ثم حفظها كملف WAV.
#
# ملاحظة مهمة:
# - هذا الملف لا يحتوي على النموذج العصبي نفسه، بل يستدعيه من repo خارجي.
# - تشغيل التحويل المحلي يحتاج عادةً "نموذجين":
#   1) Acoustic Model: يحوّل النص إلى Mel-Spectrogram (مثل FastPitch/Tacotron2).
#   2) Vocoder: يحوّل Mel إلى Waveform (مثل HiFi-GAN) — وقد يكون states_3000.pth.
# ---------------------------------------------------------------------------


# ---------------------------------------------------------------------------
# تقسيم النص (Text Segmentation)
# الغرض: تقليل طول النص في كل دفعة لتفادي مشاكل الأداء/الحدود في بعض المحركات.
# تم تعديل max_length من 500 إلى 200 لتفادي خطأ "Reached max decoder steps" في Tacotron2.
# ---------------------------------------------------------------------------
def split_text_into_segments(text: str, max_length: int = 200) -> List[str]:
    # المدخلات:
    # - text: النص الكامل (قد يكون طويلاً جداً)
    # - max_length: الحد الأقصى التقريبي لطول كل مقطع (بالأحرف)
    #
    # المخرجات:
    # - قائمة مقاطع نصية (List[str])، كل عنصر فيها جملة/مجموعة جمل أقصر
    #
    # الخوارزمية:
    # - نستبدل أسطر جديدة بمسافة.
    # - نقسم النص على ". " كفاصل مبسط للجمل.
     # - نجمع الجمل تدريجياً حتى يصل المقطع للطول الأقصى، ثم نبدأ مقطعاً جديداً.
    text = text.replace("\n", " ").replace("\r", " ").replace("\t", " ")
    words = text.split()
    if not words:
        return []

    segments: List[str] = []
    current_words: List[str] = []
    current_len = 0

    for w in words:
        extra = len(w) if not current_words else (1 + len(w))
        if current_len + extra <= max_length:
            current_words.append(w)
            current_len += extra
            continue

        segments.append(" ".join(current_words))
        current_words = [w]
        current_len = len(w)

    if current_words:
        segments.append(" ".join(current_words))

    return segments



from app.utils.text_utils import clean_text_for_tts

def _normalize_tts_text(text: str) -> str:
    return clean_text_for_tts(text)

# Global progress tracker: {book_id: {"progress": int, "status": str, "eta": float}}
generation_progress = {}

def update_progress(book_id: int, progress: int, status: str):
    generation_progress[book_id] = {"progress": progress, "status": status}

def get_progress(book_id: int):
    return generation_progress.get(book_id, {"progress": 0, "status": "idle"})

def estimate_time(text: str) -> float:
    # Estimate: ~0.1s per character is a rough guess for Tacotron2 on CPU/GPU
    # Or better: ~2-3 seconds per segment (200 chars).
    # Let's say 3 seconds per 200 chars => 0.015 s/char.
    # Adjust based on real observations (load_test showed 4s for short text).
    # Let's be conservative: 0.05 s/char.
    return len(text) * 0.05



# ---------------------------------------------------------------------------
# مسارات المشروع (Paths)
# الغرض: إيجاد مسارات ثابتة نسبيّاً بغض النظر عن مكان تشغيل السيرفر.
# ---------------------------------------------------------------------------
def _project_root_dir() -> str:
    return settings.BASE_DIR


# ---------------------------------------------------------------------------
# اكتشاف repo الخارجي (tts-arabic-pytorch)
# الغرض: معرفة أين يوجد كود التحويل المحلي حتى يمكن استيراده وتشغيله.
# ---------------------------------------------------------------------------
def _tts_arabic_repo_path() -> Optional[str]:
    # يعتمد على الإعدادات من config.py
    path = settings.TTS_ARABIC_PYTORCH_PATH
    if path and os.path.isdir(path):
        return path

    # Fallback search if config path is not found (backward compatibility)
    candidates = [
        os.path.join(_project_root_dir(), "tts-arabic-pytorch"),
        os.path.join(_project_root_dir(), "third_party", "tts-arabic-pytorch"),
    ]
    for p in candidates:
        if os.path.isdir(p):
            return p

    return None

def _models_dir() -> str:
    return os.path.join(_project_root_dir(), "app", "models")


def _pushd(path: str):
    class _Pushd:
        def __init__(self, p: str):
            self._p = p
            self._prev = None

        def __enter__(self):
            self._prev = os.getcwd()
            os.chdir(self._p)

        def __exit__(self, exc_type, exc, tb):
            if self._prev is not None:
                os.chdir(self._prev)

    return _Pushd(path)


# ---------------------------------------------------------------------------
# كتابة WAV (WAV Writing)
# الغرض: تحويل Waveform (قيم عائمة -1..1) إلى PCM 16-bit وكتابته كملف WAV.
# ---------------------------------------------------------------------------
def _write_wav(output_path: str, sampling_rate: int, wave):
    # التبعيات هنا يتم استيرادها داخل الدالة لتقليل كلفة الاستيراد عند عدم الحاجة:
    # - numpy: لتحويل الموجة إلى مصفوفة ومعالجتها
    # - scipy.io.wavfile.write: لكتابة ملف WAV
    import numpy as np
    from scipy.io.wavfile import write

    # نحول "wave" إلى مصفوفة Numpy للتعامل معها بشكل موحد
    wav = np.asarray(wave)
    if wav.ndim > 1:
        wav = wav.reshape(-1)

    # نتأكد أن القيم عائمة لتطبيق القص (clip) والتحويل إلى PCM
    if wav.dtype != np.float32 and wav.dtype != np.float64:
        wav = wav.astype(np.float32)

    # قص القيم ضمن المجال المتوقع ثم تحويلها إلى PCM 16-bit
    wav = np.clip(wav, -1.0, 1.0)
    pcm16 = (wav * 32767.0).astype(np.int16)
    write(output_path, sampling_rate, pcm16)




_CACHED_MODEL = None

from app.utils.timer import PrecisionTimer

# ---------------------------------------------------------------------------
# التحويل المحلي نص→صوت (Local TTS Pipeline)
# الغرض: تشغيل نماذج PyTorch محلياً عبر repo خارجي (tts-arabic-pytorch).
#
# المدخلات:
# - text: النص العربي المراد تحويله
# - output_path: مسار الإخراج المقترح (سيتم ضبطه إلى .wav)
#
# المخرجات:
# - مسار ملف الصوت الناتج (.wav)
#
# أهم المتغيرات البيئية المدعومة:
# - TTS_ENGINE=local                لتفعيل المحلي
# - TTS_ARABIC_PYTORCH_PATH         مسار repo tts-arabic-pytorch
# - TTS_MODEL=fastpitch|tacotron2   اختيار النموذج الصوتي
# - TTS_ACOUSTIC_CKPT               مسار checkpoint لنموذج text→mel
# - TTS_VOCODER_CKPT                مسار checkpoint للـ vocoder (مثل states_3000.pth)
# - TTS_VOCODER_CONFIG              مسار config.json للـ vocoder
# - TTS_DEVICE=auto|cpu             لتحديد استخدام GPU أو CPU
# - TTS_SAMPLING_RATE               معدل العيّنة لملف WAV (افتراضي 22050)
# ---------------------------------------------------------------------------
def _try_generate_local_tts(text: str, output_path: str, book_id: Optional[int] = None) -> str:
    global _CACHED_MODEL
    timer = PrecisionTimer("LocalTTS").start()
    
    if book_id:
        update_progress(book_id, 5, "Preprocessing")

    text = _normalize_tts_text(text)
    if not text:
        if book_id: update_progress(book_id, 0, "Failed: Empty text")
        raise RuntimeError("TTS text is empty after normalization.")

    # 1) العثور على repo الخارجي الذي يحوي كود الاستدلال (inference)
    repo_path = _tts_arabic_repo_path()
    if not repo_path:
        raise RuntimeError(
            "Local TTS enabled but tts-arabic-pytorch repo not found. "
            "Set TTS_ARABIC_PYTORCH_PATH or place repo under API_Database_Gradution_project/third_party."
        )

    model_kind = os.environ.get("TTS_MODEL", "fastpitch").strip().lower()

    # 2) تحديد مسار Acoustic Model (text→mel)
    # -----------------------------------------------------------------------
    # هام: تم تحديد هذا النموذج حصرياً بناءً على طلب المستخدم.
    # نستخدم المسار المعرف في الإعدادات بدلاً من المسار الثابت.
    # -----------------------------------------------------------------------
    acoustic_ckpt = settings.TTS_ACOUSTIC_CKPT
    
    if not os.path.exists(acoustic_ckpt):
        raise RuntimeError(
            f"CRITICAL: The required exclusive model is missing.\n"
            f"Expected path: {acoustic_ckpt}\n"
        )

    if "states_3000.pth" in acoustic_ckpt:
        model_kind = "tacotron2"
        # We know this checkpoint is Tacotron2, so we force it.
        # Also force Griffin-Lim because we don't have a matching vocoder for it.
        # (Tacotron2Wave would require a HiFi-GAN vocoder checkpoint)

    # تعطيل البحث عن نماذج أخرى
    # لن نستخدم fastpitch.pth ولا tacotron2.pth الافتراضية.
    
    # 3) تحديد مسار Vocoder (mel→waveform)
    # -----------------------------------------------------------------------
    # هام: لضمان "عدم استخدام أي نموذج آخر"، سنعطل الـ Vocoder العصبي.
    # هذا قد يؤدي لاستخدام Griffin-Lim (جودة أقل) لكنه يلتزم بالشرط الصارم.
    # -----------------------------------------------------------------------
    vocoder_ckpt = None
    vocoder_config = None

    env_model = os.environ.get("TTS_MODEL", "fastpitch").strip().lower()
    if model_kind != "tacotron2" and env_model == "fastpitch":
         # Only if NOT states_3000.pth (which we forced to tacotron2)
         pass

    # ... (rest of the code) ...
    # if env_model == "fastpitch":
    #    target_vocoder = r"c:\Users\Testing\AndroidStudioProjects\app\API_modelAI\states_3000 (1)\states_3000.pth"
    #    vocoder_ckpt = target_vocoder
    #    vocoder_config = os.path.join(_models_dir(), "config.json")
    #    if not os.path.exists(vocoder_ckpt):
    #        if book_id: update_progress(book_id, 0, "Failed: exclusive vocoder missing")
    #        raise RuntimeError("CRITICAL: exclusive vocoder model is missing")
    
    # We will use the same checkpoint for acoustic model.
    # For vocoder, we will set it to None to force Griffin-Lim if possible,
    # OR we must accept that FastPitch2Wave requires a HiFi-GAN checkpoint.
    # BUT states_3000.pth is an ACOUSTIC model checkpoint (FastPitch).
    # It cannot be loaded as HiFi-GAN.
    # So we MUST use Griffin-Lim.
    vocoder_ckpt = None
    vocoder_config = None

    # لا حاجة للبحث عن vocoder config لأننا لن نستخدم vocoder.

    # 5) ضمان أن الإخراج سيكون WAV لأن التشغيل المحلي هنا يكتب WAV فقط
    if output_path.lower().endswith(".mp3"):
        output_path = output_path[:-4] + ".wav"
    elif not output_path.lower().endswith(".wav"):
        output_path = output_path + ".wav"

    # 6) إضافة repo لمسارات الاستيراد حتى نتمكن من import models.* من المشروع الخارجي
    if repo_path in sys.path:
        sys.path.remove(repo_path)
    sys.path.insert(0, repo_path)
    if (
        "models" in sys.modules
        and not hasattr(sys.modules["models"], "__path__")
        and getattr(sys.modules["models"].__class__, "__module__", "") != "unittest.mock"
    ):
        del sys.modules["models"]

    timer.checkpoint("Setup")
    if book_id: update_progress(book_id, 10, "Setup complete")

    # تقسيم النص (Segmentation) - تحسين للأداء مع النصوص الطويلة
    segments = split_text_into_segments(text)
    
    # Prepare input for batch processing
    
    timer.checkpoint(f"Segmentation ({len(segments)} segments)")
    if book_id: update_progress(book_id, 15, f"Segmented into {len(segments)} parts")

    with _pushd(repo_path):
        try:
            import torch
            device = "cuda" if torch.cuda.is_available() and os.environ.get("TTS_DEVICE", "auto") != "cpu" else "cpu"
        except Exception:
            torch = None
            device = "cpu"

        if _CACHED_MODEL is None:
            if book_id:
                update_progress(book_id, 20, "Loading Model...")

            if "states_3000.pth" in acoustic_ckpt:
                model_kind = "tacotron2"

            # import models as _ext_models  <-- This was causing issues as models/__init__.py is empty

            if model_kind == "tacotron2":
                try:
                    import models.tacotron2 as tacotron2_mod
                    Tacotron2Wave = getattr(tacotron2_mod, "Tacotron2Wave")
                    if vocoder_ckpt is None:
                        logger.info("Loading Tacotron2Wave (acoustic only) with Griffin-Lim")
                        model = Tacotron2Wave(acoustic_ckpt)
                        model.use_griffin_lim = True
                    else:
                        if vocoder_config:
                            model = Tacotron2Wave(acoustic_ckpt, vocoder_ckpt, vocoder_config)
                        else:
                            model = Tacotron2Wave(acoustic_ckpt, vocoder_ckpt)
                    model = model.to(device)
                    _CACHED_MODEL = model
                except Exception as e:
                    logger.error(f"Failed to import Tacotron2Wave: {e}")
                    raise RuntimeError("Tacotron2 model not available. Please check model installation.")
            elif model_kind == "fastpitch":
                try:
                    import models.fastpitch as fastpitch_mod
                    FastPitch2Wave = getattr(fastpitch_mod, "FastPitch2Wave")
                    model = FastPitch2Wave(acoustic_ckpt, vocoder_ckpt, vocoder_config)
                    model = model.to(device)
                    _CACHED_MODEL = model
                except Exception as e:
                    logger.error(f"Failed to import FastPitch2Wave: {e}")
                    raise RuntimeError("FastPitch model not available. Please check model installation.")
            else:
                raise RuntimeError("Unsupported model kind in local TTS")
        
        timer.checkpoint("Model Loading/Check")

        # Process segments in chunks to update progress
        all_waves = []
        total_segments = len(segments)
        batch_size = 10 # Process 10 segments at a time for better performance
        
        # Note: The model's tts() method automatically handles Arabic-to-Buckwalter conversion
        # and phoneme processing via the 'text' module (as arabic_in=True by default).
        # We don't need to manually preprocess here.

        for i in range(0, total_segments, batch_size):
            batch = segments[i:i+batch_size]
            current_progress = 25 + int((i / total_segments) * 70)
            if book_id: update_progress(book_id, current_progress, f"Processing batch {i//batch_size + 1}/{(total_segments+batch_size-1)//batch_size}")
            
            try:
                if getattr(_CACHED_MODEL, 'use_griffin_lim', False):
                    # 1. Get Mel Spectrogram
                    # Note: Tacotron2Wave wrapper doesn't have ttmel, but its .model member does.
                    if hasattr(_CACHED_MODEL, 'model') and hasattr(_CACHED_MODEL.model, 'ttmel'):
                        mel_list = _CACHED_MODEL.model.ttmel(batch, batch_size=batch_size)
                    else:
                        # Fallback just in case, though we expect .model.ttmel to exist for Tacotron2
                        mel_list = _CACHED_MODEL.ttmel(batch, batch_size=batch_size)
                    
                    import numpy as np
                    try:
                        import librosa
                    except Exception:
                        librosa = None
                    
                    for mel in mel_list:
                        mel_np = mel.cpu().numpy()
                        try:
                            if librosa is None:
                                all_waves.append(torch.zeros(mel.shape[1] * 256))
                            else:
                                S_inv = librosa.feature.inverse.mel_to_stft(
                                    np.exp(mel_np),
                                    sr=22050,
                                    n_fft=1024,
                                    fmin=0,
                                    fmax=8000,
                                )
                                wave_np = librosa.griffinlim(
                                    S_inv,
                                    n_iter=32,
                                    hop_length=256,
                                    win_length=1024,
                                )
                                all_waves.append(torch.from_numpy(wave_np).float())
                        except Exception as gle:
                            logger.error(f"Griffin-Lim failed: {gle}")
                            all_waves.append(torch.zeros(mel.shape[1] * 256))
                else:
                    wave_out = _CACHED_MODEL.tts(batch)
                    if isinstance(wave_out, list):
                        for w in wave_out:
                            all_waves.append(w.reshape(-1).cpu())
                    elif isinstance(wave_out, torch.Tensor):
                        all_waves.append(wave_out.reshape(-1).cpu())
                    else:
                        all_waves.append(torch.tensor(wave_out).reshape(-1).cpu())

            except Exception as e:
                logger.error(f"Error processing batch {i}: {e}")
                # Don't fail the whole book if one batch fails? 
                # Or fail fast?
                # Failing fast is safer for data integrity.
                raise e

        if book_id: update_progress(book_id, 95, "Stitching audio...")
            
        # Concatenate all waves
        if all_waves:
            wave = torch.cat(all_waves, dim=0)
        else:
            wave = torch.tensor([])

    # 10) حفظ الناتج على القرص كملف WAV (PCM 16-bit)
    if book_id: update_progress(book_id, 95, "Saving audio file")
    sampling_rate = int(os.environ.get("TTS_SAMPLING_RATE", "22050"))
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    _write_wav(output_path, sampling_rate, wave)
    
    if book_id: update_progress(book_id, 100, "Completed")
    timer.stop()
    return output_path



# ---------------------------------------------------------------------------
# الواجهة العامة للخدمة (Public API)
# الغرض: هذه هي الدالة التي تستدعيها المسارات (routes) لتوليد ملف الصوت.
#
# المدخلات:
# - text: النص المراد تحويله
# - output_path: مسار الملف المراد إنتاجه (سيتم تحويله إلى WAV في المحلي)
# - lang: معامل احتياطي للتوافق (غير مستخدم في المحلي حالياً)
#
# المخرجات:
# - مسار الملف الصوتي النهائي الذي تم توليده
#
# ملاحظة تشغيل:
# - حالياً هذا المشروع مُهيأ لإجبار التشغيل المحلي فقط.
#   إذا لم يتم تفعيل TTS_ENGINE=local سيتم رفع خطأ واضح.
# ---------------------------------------------------------------------------
def generate_tts_audio(text: str, output_path: str, lang: str = "ar", book_id: Optional[int] = None):
    engine = os.environ.get("TTS_ENGINE", "").strip().lower()
    if engine in {"gtts", "google"}:
        raise RuntimeError("gTTS is disabled for this project. Use local TTS instead.")

    return _try_generate_local_tts(text, output_path, book_id)
