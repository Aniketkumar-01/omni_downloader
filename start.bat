@echo off
setlocal enabledelayedexpansion

title OmniDownloader
echo ========================================================
echo               OmniDownloader Quick Launcher             
echo ========================================================
echo.

:: Check Python installation
python --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Python is not installed or not in PATH.
    echo Please install Python 3.10+ from https://python.org and ensure
    echo 'Add Python to PATH' is checked during installation.
    echo.
    pause
    exit /b 1
)

:: Ensure virtual environment exists
if not exist "venv\Scripts\activate.bat" (
    echo [INFO] Creating Python virtual environment...
    python -m venv venv
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
)

:: Activate virtual environment
call venv\Scripts\activate.bat

:: Install/verify dependencies
echo [INFO] Verifying dependencies...
pip install -r backend\requirements.txt --quiet
if %ERRORLEVEL% neq 0 (
    echo [WARNING] Dependency install check had issues. Continuing to launch...
)

:: Launch the application
echo [INFO] Starting OmniDownloader...
python launcher.py

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Application exited with error code %ERRORLEVEL%.
    pause
)
