@echo off
title KAVACH Stack Launcher
color 0B

echo ===================================================
echo            KAVACH ONE-CLICK BOOT LOADER
echo ===================================================
echo [+] Current Path: C:\Users\kavach\Desktop\kavach\kavach\backend
echo [+] Virtual Env:  venv (Python 3.12)
echo ===================================================
echo.

:: Ask user if they want to run the named tunnel or the temporary quick tunnel
echo Select Cloudflare Tunnel Mode:
echo [1] Named Tunnel: kavach-api (api.pmsraebareli.online)
echo [2] Quick Tunnel: trycloudflare.com (Temporary Public URL)
echo.
set /p mode="Enter choice [1 or 2, default is 1]: "

if "%mode%"=="2" (
    set TUNNEL_CMD="C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8000
    echo [+] Starting Quick Tunnel...
) else (
    set TUNNEL_CMD=cloudflared tunnel --edge-ip-version 4 run kavach-api
    echo [+] Starting Named Tunnel 'kavach-api'...
)

echo.
echo ===================================================
echo [1/2] Launching Cloudflare Tunnel...
echo ===================================================
start "Kavach - Cloudflare Tunnel" cmd /k "color 0E && echo [*] Initializing Tunnel... && %TUNNEL_CMD%"

echo.
echo [+] Waiting 5 seconds for tunnel handshake to complete...
timeout /t 5 >nul

echo.
echo ===================================================
echo [2/2] Launching Kavach Django Daphne ASGI Server...
echo ===================================================
cd /d "C:\Users\kavach\Desktop\kavach\kavach\backend"
start "Kavach - Django Daphne ASGI" cmd /k "color 0A && echo [*] Activating virtual environment... && call venv\Scripts\activate.bat && set PYTHONPATH=. && echo [*] Starting ASGI/Daphne Dev Server on port 8000... && python manage.py runserver 127.0.0.1:8000"

echo.
echo ===================================================
echo             KAVACH STACK BOOTED SUCCESSFULLY!
echo ===================================================
echo [!] Keep this window open if you want to close both.
echo [!] To shutdown the stack, close the separate CMD windows.
echo.
pause
