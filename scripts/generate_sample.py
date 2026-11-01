import os
import sys
import glob
import torch
import soundfile as sf
import argparse
from contextlib import contextmanager

# Add project root to sys.path
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)

# Add tts-arabic-pytorch to sys.path
repo_path = os.path.join(project_root, "API_Database_Gradution_project", "third_party", "tts-arabic-pytorch")
sys.path.append(repo_path)

from API_Database_Gradution_project.app.utils.text_utils import clean_text_for_tts

@contextmanager
def _pushd(new_dir):
    previous_dir = os.getcwd()
    os.chdir(new_dir)
    try:
        yield
    finally:
        os.chdir(previous_dir)

def get_latest_checkpoint(checkpoint_dir):
    checkpoints = glob.glob(os.path.join(checkpoint_dir, "states_*.pth"))
    if not checkpoints:
        return None
    # Sort by number in filename
    try:
        checkpoints.sort(key=lambda x: int(x.split("_")[-1].split(".")[0]))
        return checkpoints[-1]
    except ValueError:
        return None

def generate_sample(text_input, output_path):
    checkpoint_dir = os.path.join(project_root, "training_data", "checkpoints")
    checkpoint_path = get_latest_checkpoint(checkpoint_dir)
    
    if not checkpoint_path:
        print("No checkpoints found in", checkpoint_dir)
        return

    # Paths for HiFi-GAN Vocoder
    vocoder_sd = os.path.join(repo_path, "pretrained", "hifigan-asc-v1", "hifigan-asc.pth")
    vocoder_config = os.path.join(repo_path, "pretrained", "hifigan-asc-v1", "config.json")

    print(f"Using checkpoint: {checkpoint_path}")
    if os.path.exists(vocoder_sd):
        print(f"Using HiFi-GAN vocoder: {vocoder_sd}")
    else:
        print("Warning: HiFi-GAN not found, falling back to Griffin-Lim logic might be needed (not implemented in this simplified script yet)")
    
    # Run inside repo path to ensure relative imports work
    with _pushd(repo_path):
        from models.tacotron2 import Tacotron2Wave
        
        device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"Using device: {device}")
        
        # Initialize Tacotron2Wave with HiFi-GAN
        # Tacotron2Wave automatically loads the model and vocoder
        model = Tacotron2Wave(
            model_sd_path=checkpoint_path,
            vocoder_sd=vocoder_sd,
            vocoder_config=vocoder_config,
            arabic_in=True
        )
        
        model = model.to(device)
        model.eval()
        
        # Clean text
        cleaned_text = clean_text_for_tts(text_input)
        print(f"Original text: {text_input}")
        print(f"Cleaned text: {cleaned_text}")
        
        # Generate Audio
        print("Generating audio with HiFi-GAN...")
        with torch.no_grad():
            # tts_single returns wave tensor
            wave = model.tts_single(cleaned_text, speaker_id=0)
        
        # Convert to numpy
        wave_np = wave.cpu().numpy()
        
        # Save
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        # HiFi-GAN usually outputs at 22050Hz (check config if different, but standard is 22050)
        sf.write(output_path, wave_np, 22050)
        print(f"Saved sample to: {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", type=str, default="بسم الله الرحمن الرحيم")
    parser.add_argument("--out", type=str, default="sample_hifigan.wav")
    args = parser.parse_args()
    
    output_full_path = os.path.join(project_root, "training_data", "samples", args.out)
    generate_sample(args.text, output_full_path)
