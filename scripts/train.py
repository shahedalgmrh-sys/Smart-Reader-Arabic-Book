import os
import sys
import subprocess
import glob
import yaml

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

def update_config_with_checkpoint(config_path, checkpoint_path):
    # Read the config file
    with open(config_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Write back with updated restore_model
    with open(config_path, 'w', encoding='utf-8') as f:
        for line in lines:
            if line.strip().startswith('restore_model:'):
                f.write(f'restore_model: {checkpoint_path}\n')
            else:
                f.write(line)

def train():
    project_root = r"c:\Users\Testing\AndroidStudioProjects\app"
    repo_path = os.path.join(project_root, "API_Database_Gradution_project", "third_party", "tts-arabic-pytorch")
    config_path = os.path.join(project_root, "training_data", "albaraa_config.yaml")
    checkpoint_dir = os.path.join(project_root, "training_data", "checkpoints")
    
    # Check if config exists
    if not os.path.exists(config_path):
        print(f"Config not found: {config_path}")
        return

    # Auto-resume logic
    latest_checkpoint = get_latest_checkpoint(checkpoint_dir)
    if latest_checkpoint:
        print(f"Found latest checkpoint: {latest_checkpoint}")
        print("Updating config to resume from this checkpoint...")
        update_config_with_checkpoint(config_path, latest_checkpoint)
    else:
        print("No existing checkpoints found. Starting from config default.")

    # Construct command
    cmd = [sys.executable, "train.py", "--config", config_path]
    
    print(f"Starting training in {repo_path}...")
    print(f"Command: {' '.join(cmd)}")
    print("Logs will be saved to training_data/logs")
    print("Checkpoints will be saved to training_data/checkpoints")
    
    # Run
    try:
        subprocess.run(cmd, cwd=repo_path)
    except KeyboardInterrupt:
        print("\nTraining interrupted by user.")

if __name__ == "__main__":
    train()
