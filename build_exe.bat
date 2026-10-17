@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo ========================================================
echo   OmniDownloader - Standalone Executable (.exe) Builder
echo ========================================================
echo.

:: 1. Detect if existing venv was created with incompatible Python 3.14
if exist "venv\pyvenv.cfg" (
    findstr /i "version = 3.14" "venv\pyvenv.cfg" >nul 2>nul
    if !errorlevel! equ 0 (
        echo [!] Detected virtual environment built with Python 3.14 (pre-release).
        echo [*] Removing broken Python 3.14 venv...
        rmdir /s /q "venv" >nul 2>nul
    )
)

:: 2. Search for stable Python release (3.12, 3.11, 3.10, or 3.13)
set "PY_CMD="

py -3.12 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.12" & goto :python_found

py -3.11 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.11" & goto :python_found

py -3.10 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.10" & goto :python_found

py -3.13 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.13" & goto :python_found

if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" set "PY_CMD=\"%LOCALAPPDATA%\Programs\Python\Python312\python.exe\"" & goto :python_found
if exist "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" set "PY_CMD=\"%LOCALAPPDATA%\Programs\Python\Python311\python.exe\"" & goto :python_found
if exist "%LOCALAPPDATA%\Programs\Python\Python310\python.exe" set "PY_CMD=\"%LOCALAPPDATA%\Programs\Python\Python310\python.exe\"" & goto :python_found
if exist "C:\Program Files\Python312\python.exe" set "PY_CMD=\"C:\Program Files\Python312\python.exe\"" & goto :python_found
if exist "C:\Program Files\Python311\python.exe" set "PY_CMD=\"C:\Program Files\Python311\python.exe\"" & goto :python_found
if exist "C:\Program Files\Python310\python.exe" set "PY_CMD=\"C:\Program Files\Python310\python.exe\"" & goto :python_found

python -c "import sys; sys.exit(0 if sys.version_info < (3, 14) and sys.version_info >= (3, 10) else 1)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=python" & goto :python_found

echo [WARNING] Only Python 3.14 (pre-release) was detected.
echo Python 3.14 causes 'ModuleNotFoundError: No module named _pydantic_core'.
echo A stable Python release (3.11 or 3.12) is required to build the executable.
echo.
set /p install_py="Would you like to install Python 3.12 automatically via winget? (Y/n): "
if /i "!install_py!"=="n" (
    echo Please install Python 3.12 or 3.11 from https://www.python.org/downloads/
    pause
    exit /b 1
)

echo [*] Installing Python 3.12 via winget...
winget install Python.Python.3.12 --silent --accept-package-agreements --accept-source-agreements
if !errorlevel! neq 0 (
    echo [ERROR] winget installation failed. Please install Python 3.12 manually from python.org
    pause
    exit /b 1
)

if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" (
    set "PY_CMD=\"%LOCALAPPDATA%\Programs\Python\Python312\python.exe\""
) else (
    set "PY_CMD=py -3.12"
)

:python_found
echo [*] Using Python interpreter: !PY_CMD!

if not exist "venv" (
    echo [*] Creating virtual environment...
    !PY_CMD! -m venv venv
    if !errorlevel! neq 0 (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
)

call venv\Scripts\activate.bat

echo [*] Checking and preparing build dependencies...
pip install pyinstaller -r backend\requirements.txt --quiet

echo [*] Packaging OmniDownloader into standalone .exe (PyInstaller)...
pyinstaller --clean omnidownloader.spec

if !errorlevel! neq 0 (
    echo.
    echo [ERROR] Build failed. Please inspect the logs above.
    pause
    exit /b 1
)

echo.
echo ========================================================
echo   BUILD SUCCESSFUL!
echo   Executable generated at: %~dp0dist\OmniDownloader.exe
echo ========================================================
echo.

set /p launch="Would you like to launch OmniDownloader.exe now? (Y/n): "
if /i "!launch!"=="n" (
    exit /b 0
)

start "" "%~dp0dist\OmniDownloader.exe"
