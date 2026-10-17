@echo off
setlocal
cd /d "%~dp0"

if exist "dist\OmniDownloader.exe" (
    echo [*] Launching compiled standalone desktop app...
    start "" "%~dp0dist\OmniDownloader.exe"
    exit /b 0
)

echo [*] Compiled .exe not found in dist\. Launching via local desktop shell...
call "%~dp0start.bat"
