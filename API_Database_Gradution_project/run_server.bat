@echo off
setlocal
set PORT=%1
if "%PORT%"=="" set PORT=8000
cd /d "%~dp0"
python -m uvicorn app.main:app --host 0.0.0.0 --port %PORT%
