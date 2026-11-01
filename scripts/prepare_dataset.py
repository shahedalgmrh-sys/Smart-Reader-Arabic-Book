import os
import re
import sys
import logging
import soundfile as sf
import librosa
import numpy as np
from datetime import datetime, timedelta

# Add project root to path to import app modules
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "API_Database_Gradution_project")))

from app.utils.text_utils import clean_text_for_tts

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("dataset_prep")

def parse_ass_time(time_str):
    """Parses ASS timestamp (h:mm:ss.cc) to seconds."""
    # Example: 0:00:05.77
    try:
        parts = time_str.split(':')
        h = int(parts[0])
        m = int(parts[1])
        s_parts = parts[2].split('.')
        s = int(s_parts[0])
        cs = int(s_parts[1]) if len(s_parts) > 1 else 0
        
        total_seconds = h * 3600 + m * 60 + s + cs / 100.0
        return total_seconds
    except ValueError:
        return 0.0

def parse_ass_file(ass_path):
    """Parses .ass file and returns list of (start, end, text) tuples."""
    events = []
    with open(ass_path, 'r', encoding='utf-8-sig') as f:
        is_events = False
        format_order = []
        for line in f:
            line = line.strip()
            if line == '[Events]':
                is_events = True
                continue
            
            if not is_events:
                continue
                
            if line.startswith('Format:'):
                # Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                parts = line[7:].split(',')
                format_order = [p.strip() for p in parts]
                continue
                
            if line.startswith('Dialogue:'):
                # Dialogue: 0,0:00:00.00,0:00:05.77,Default,[00,0,0,0,,زمن جميل كان فيه سمونا
                # The text part might contain commas, so we split by comma only for the header fields
                parts = line[9:].split(',', len(format_order) - 1)
                
                if len(parts) != len(format_order):
                    continue
                    
                data = dict(zip(format_order, parts))
                start = parse_ass_time(data.get('Start', '0:00:00.00'))
                end = parse_ass_time(data.get('End', '0:00:00.00'))
                text = data.get('Text', '')
                
                # Remove ASS tags like {\pos...} or [00...]
                text = re.sub(r'\{.*?\}', '', text)
                text = re.sub(r'\[.*?\]', '', text) # Remove [00:00] timestamps in text if any
                
                events.append({
                    'start': start,
                    'end': end,
                    'text': text
                })
    return events

def prepare_dataset(audio_path, ass_path, output_dir, sample_rate=22050):
    os.makedirs(output_dir, exist_ok=True)
    wavs_dir = os.path.join(output_dir, "wavs")
    os.makedirs(wavs_dir, exist_ok=True)
    
    metadata_path = os.path.join(output_dir, "metadata.txt")
    
    logger.info(f"Loading audio: {audio_path}")
    # Load full audio
    # Note: librosa.load resamples to sr=22050 by default, which is what we want for Tacotron2
    audio, sr = librosa.load(audio_path, sr=sample_rate)
    
    logger.info(f"Parsing ASS: {ass_path}")
    events = parse_ass_file(ass_path)
    
    logger.info(f"Found {len(events)} segments. Processing...")
    
    valid_segments = 0
    with open(metadata_path, 'w', encoding='utf-8') as meta_file:
        for i, event in enumerate(events):
            start_sample = int(event['start'] * sr)
            end_sample = int(event['end'] * sr)
            
            # Padding (optional, maybe 0.1s)
            # start_sample = max(0, start_sample - int(0.1 * sr))
            # end_sample = min(len(audio), end_sample + int(0.1 * sr))
            
            if end_sample <= start_sample:
                continue
                
            # Extract audio segment
            segment_audio = audio[start_sample:end_sample]
            
            # Check for minimum length (e.g. 0.5s)
            if len(segment_audio) < 0.5 * sr:
                continue

            # Clean text
            raw_text = event['text']
            cleaned_text = clean_text_for_tts(raw_text)
            
            if not cleaned_text:
                continue
                
            filename = f"albaraa_{i:04d}.wav"
            filepath = os.path.join(wavs_dir, filename)
            
            # Write WAV
            sf.write(filepath, segment_audio, sr)
            
            # Write Metadata: "filename" "arabic_text"
            # The ArabDataset uses regex: "(?P<filename>.*)" "(?P<phonemes>.*)"
            # But we want to inject ARABIC so it converts it.
            # We will use the key "arabic" in our custom regex later, OR we can trick it.
            # If we write: "filename" "arabic_text", we can tell the dataset to use pattern:
            # "(?P<filename>.*)" "(?P<arabic>.*)"
            
            meta_file.write(f'"{filename}" "{cleaned_text}"\n')
            valid_segments += 1
            
    logger.info(f"Done. Processed {valid_segments} valid segments.")
    logger.info(f"Dataset saved to: {output_dir}")

if __name__ == "__main__":
    # Hardcoded paths based on user context
    BASE_DIR = r"c:\Users\Testing\AndroidStudioProjects\app"
    AUDIO_FILE = os.path.join(BASE_DIR, "abtalislam", "albaraa", "albaraa.mp3")
    ASS_FILE = os.path.join(BASE_DIR, "abtalislam", "albaraa", "transcript.ass")
    OUTPUT_DIR = os.path.join(BASE_DIR, "training_data", "albaraa")
    
    if not os.path.exists(AUDIO_FILE):
        logger.error(f"Audio file not found: {AUDIO_FILE}")
        sys.exit(1)
        
    prepare_dataset(AUDIO_FILE, ASS_FILE, OUTPUT_DIR)
