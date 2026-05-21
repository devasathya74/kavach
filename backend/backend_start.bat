@echo off
title Kavach Backend ASGI Server (Daphne)
echo ====================================================
echo Starting Kavach Production-Like ASGI Server...
echo ====================================================

:: Navigate to the backend directory
cd /d "C:\Users\kavach\Desktop\kavach\kavach\backend"

:: Verify virtual environment exists
if not exist "venv\Scripts\activate.bat" (
    echo [ERROR] Virtual environment not found. Please ensure venv is created.
    pause
    exit /b 1
)

:: Activate virtual environment
call venv\Scripts\activate.bat

:: Set PYTHONPATH so python can find the kavach_backend modules
set PYTHONPATH=.

:: Run Daphne ASGI server
echo Starting Daphne on http://0.0.0.0:8000 ...
daphne -b 0.0.0.0 -p 8000 kavach_backend.asgi:application

pause
