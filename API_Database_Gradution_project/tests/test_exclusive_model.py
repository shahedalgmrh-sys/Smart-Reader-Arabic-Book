import sys
import os
import unittest
from unittest.mock import patch, MagicMock

# Adjust path to include the project root so we can import app
project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from app.services import ai_service

class TestExclusiveModel(unittest.TestCase):
    
    @patch("app.services.ai_service._tts_arabic_repo_path")
    @patch("app.services.ai_service._pushd")
    @patch("app.services.ai_service.os.path.exists")
    def test_exclusive_model_is_used_as_acoustic(self, mock_exists, mock_pushd, mock_repo_path):
        ai_service._CACHED_MODEL = None
        # Setup mocks
        mock_repo_path.return_value = "/mock/repo"
        
        target_model = ai_service.settings.TTS_ACOUSTIC_CKPT
        
        def side_effect(path):
            if os.path.normpath(path) == os.path.normpath(target_model):
                return True
            if "config.json" in path:
                return True 
            return False
            
        mock_exists.side_effect = side_effect
        
        # Mock internal imports
        mock_tacotron_class = MagicMock()
        mock_model_instance = MagicMock()
        mock_tacotron_class.return_value = mock_model_instance
        mock_model_instance.tts.return_value = [MagicMock()] 
        
        mock_models = MagicMock()
        mock_models.tacotron2.Tacotron2Wave = mock_tacotron_class
        
        with patch.dict(sys.modules, {"models": mock_models, "models.tacotron2": mock_models.tacotron2}):
            with patch.dict(sys.modules, {"torch": MagicMock()}):
                with patch("app.services.ai_service.split_text_into_segments", return_value=["test"]):
                    with patch("app.services.ai_service._write_wav"):
                        with patch.dict(os.environ, {"TTS_ENGINE": "local"}):
                            
                            ai_service.generate_tts_audio("hello", "output.wav")
                            
                            args, _ = mock_tacotron_class.call_args
                            
                            self.assertEqual(len(args), 1, "Should call Tacotron2Wave with only 1 argument (acoustic_ckpt)")
                            used_acoustic = args[0]
                            self.assertEqual(used_acoustic, target_model, 
                                            f"Acoustic path mismatch! Expected {target_model}, got {used_acoustic}")

if __name__ == "__main__":
    unittest.main()
