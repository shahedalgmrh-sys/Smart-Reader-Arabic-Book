import sys
import os
import unittest
from unittest.mock import patch, MagicMock

# Adjust path to include the project root so we can import app
project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from app.services import ai_service

class TestVocoderEnforcement(unittest.TestCase):
    
    @patch("app.services.ai_service._tts_arabic_repo_path")
    @patch("app.services.ai_service._pushd")
    @patch("app.services.ai_service.os.path.exists")
    @patch("app.services.ai_service._models_dir")
    def test_vocoder_path_is_enforced(self, mock_models_dir, mock_exists, mock_pushd, mock_repo_path):
        ai_service._CACHED_MODEL = None
        # Setup mocks
        mock_repo_path.return_value = "/mock/repo"
        mock_models_dir.return_value = "/mock/app/models"
        
        def side_effect(path):
            if "config.json" in path:
                return True # Pretend config exists
            if path.endswith(".pth"): # Acoustic models
                return True
            return False
            
        mock_exists.side_effect = side_effect
        
        mock_tacotron_class = MagicMock()
        mock_model_instance = MagicMock()
        mock_tacotron_class.return_value = mock_model_instance
        mock_model_instance.tts.return_value = [MagicMock()] 

        mock_fastpitch_class = MagicMock()

        mock_models = MagicMock()
        mock_models.tacotron2.Tacotron2Wave = mock_tacotron_class
        mock_models.fastpitch.FastPitch2Wave = mock_fastpitch_class

        with patch.dict(sys.modules, {"models": mock_models, "models.tacotron2": mock_models.tacotron2, "models.fastpitch": mock_models.fastpitch}):
            mock_torch = MagicMock()
            mock_torch.cuda.is_available.return_value = False
            with patch.dict(sys.modules, {"torch": mock_torch}):
                with patch("app.services.ai_service.split_text_into_segments", return_value=["test"]):
                    with patch("app.services.ai_service._write_wav"):
                        with patch.dict(os.environ, {"TTS_ENGINE": "local", "TTS_MODEL": "fastpitch"}):
                            ai_service.generate_tts_audio("hello", "output.wav")

                            self.assertTrue(mock_tacotron_class.called, "Expected Tacotron2Wave to be used for the exclusive states_3000.pth model")
                            self.assertFalse(mock_fastpitch_class.called, "FastPitch2Wave should not be used when the exclusive Tacotron2 model is forced")

    @patch("app.services.ai_service._tts_arabic_repo_path")
    @patch("app.services.ai_service._pushd")
    @patch("app.services.ai_service.os.path.exists")
    def test_does_not_fail_when_vocoder_missing(self, mock_exists, mock_pushd, mock_repo_path):
        ai_service._CACHED_MODEL = None
        mock_repo_path.return_value = "/mock/repo"

        def side_effect(path):
            if "config.json" in path:
                return True
            if path.endswith(".pth"):
                return True
            return True
        mock_exists.side_effect = side_effect

        mock_tacotron_class = MagicMock()
        mock_model_instance = MagicMock()
        mock_tacotron_class.return_value = mock_model_instance
        mock_model_instance.tts.return_value = [MagicMock()] 

        mock_models = MagicMock()
        mock_models.tacotron2.Tacotron2Wave = mock_tacotron_class

        with patch.dict(sys.modules, {"models": mock_models, "models.tacotron2": mock_models.tacotron2}):
            mock_torch = MagicMock()
            mock_torch.cuda.is_available.return_value = False
            with patch.dict(sys.modules, {"torch": mock_torch}):
                with patch("app.services.ai_service.split_text_into_segments", return_value=["test"]):
                    with patch("app.services.ai_service._write_wav"):
                        with patch.dict(os.environ, {"TTS_ENGINE": "local"}):
                            ai_service.generate_tts_audio("test", "out.wav")

if __name__ == "__main__":
    unittest.main()
