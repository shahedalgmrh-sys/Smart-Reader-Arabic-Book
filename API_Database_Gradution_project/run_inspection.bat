@echo off
cd /d "%~dp0"
set VENV_PYTHON=%~dp0venv\Scripts\python.exe

if not exist "%VENV_PYTHON%" (
    echo Error: Virtual environment not found at %VENV_PYTHON%
    echo Please create the venv first.
    pause
    exit /b 1
)

echo Installing PyTorch (CPU version) to save space...
"%VENV_PYTHON%" -m pip install torch --index-url https://download.pytorch.org/whl/cpu
if %errorlevel% neq 0 (
    echo Failed to install PyTorch.
    pause
    exit /b %errorlevel%
)

echo.
echo Running Model Inspection...
"%VENV_PYTHON%" "..\API_modelAI\inspect_model.py"
pause