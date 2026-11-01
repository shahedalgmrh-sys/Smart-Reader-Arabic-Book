from fastapi.testclient import TestClient
from fastapi import FastAPI
from app.main import app, global_exception_handler
from app.routes import get_db
from unittest.mock import MagicMock, patch
import pytest

# raise_server_exceptions=False allows the app's exception handlers to run
# instead of just crashing the test client.
client = TestClient(app, raise_server_exceptions=False)

def test_health_check_success():
    """Test that health check works when DB is fine."""
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "connected"}

def test_health_check_failure():
    """Test that health check returns 503 when DB fails."""
    # Ensure no test override masks the real get_db behavior
    original_overrides = dict(app.dependency_overrides)
    app.dependency_overrides.clear()
    try:
        with patch("app.routes.SessionLocal") as mock_session:
            mock_session.side_effect = Exception("DB Connection Failed Simulated")
            response = client.get("/health")
            assert response.status_code == 503
            data = response.json()
            assert "Database Service Unavailable" in data["detail"]
    finally:
        app.dependency_overrides = original_overrides

def test_global_exception_handler():
    """Test that unhandled exceptions return 500 JSON."""
    test_app = FastAPI()
    test_app.add_exception_handler(Exception, global_exception_handler)

    @test_app.get("/force_error")
    def force_error():
        raise ValueError("Unexpected Crash")
    
    client_local = TestClient(test_app, raise_server_exceptions=False)
    response = client_local.get("/force_error")
    assert response.status_code == 500
    data = response.json()
    assert data["detail"] == "Internal Server Error"
    assert "Unexpected Crash" in data["error_message"]
    assert data["path"] == "/force_error"
