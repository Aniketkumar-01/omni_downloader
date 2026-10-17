@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo ========================================================
echo   OmniDownloader - Windows Desktop Quick Launcher
echo ========================================================
echo.

:: 1. Detect if existing venv was created with incompatible Python 3.14
if exist "venv\pyvenv.cfg" (
    findstr /i "version = 3.14" "venv\pyvenv.cfg" >nul 2>nul
    if !errorlevel! equ 0 (
        echo [!] Detected virtual environment built with Python 3.14 (pre-release).
        echo [!] Python 3.14 lacks pre-compiled wheels for Pydantic/Rust (ModuleNotFoundError: _pydantic_core).
        echo [*] Automatically removing broken Python 3.14 venv...
        rmdir /s /q "venv" >nul 2>nul
    )
)

:: 2. Search for stable Python release (3.12, 3.11, 3.10, or 3.13)
set "PY_CMD="

:: Try py launcher with stable versions first
py -3.12 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.12" & goto :python_found

py -3.11 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.11" & goto :python_found

py -3.10 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.10" & goto :python_found

py -3.13 -c "import sys; print(sys.version)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=py -3.13" & goto :python_found

:: Check common Windows installation paths
if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" set "PY_CMD=\"%LOCALAPPDATA%\Programs\Python\Python312\python.exe\"" & goto :python_found
if exist "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" set "PY_CMD=\"%LOCALAPPDATA%\Programs\Python\Python311\python.exe\"" & goto :python_found
if exist "%LOCALAPPDATA%\Programs\Python\Python310\python.exe" set "PY_CMD=\"%LOCALAPPDATA%\Programs\Python\Python310\python.exe\"" & goto :python_found
if exist "C:\Program Files\Python312\python.exe" set "PY_CMD=\"C:\Program Files\Python312\python.exe\"" & goto :python_found
if exist "C:\Program Files\Python311\python.exe" set "PY_CMD=\"C:\Program Files\Python311\python.exe\"" & goto :python_found
if exist "C:\Program Files\Python310\python.exe" set "PY_CMD=\"C:\Program Files\Python310\python.exe\"" & goto :python_found

:: Check default 'python' on PATH (valid if between 3.10 and 3.13)
python -c "import sys; sys.exit(0 if sys.version_info < (3, 14) and sys.version_info >= (3, 10) else 1)" >nul 2>nul
if !errorlevel! equ 0 set "PY_CMD=python" & goto :python_found

:: If only Python 3.14 is installed or no stable python is found
echo [WARNING] Only Python 3.14 (pre-release) was detected.
echo Python 3.14 causes 'ModuleNotFoundError: No module named _pydantic_core'.
echo A stable Python release (3.11 or 3.12) is required.
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

:: 3. Setup Virtual Environment
if not exist "venv" (
    echo [*] Creating fresh Python virtual environment...
    !PY_CMD! -m venv venv
    if !errorlevel! neq 0 (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
)

:: 4. Activate Virtual Environment
echo [*] Activating virtual environment...
call venv\Scripts\activate.bat

:: 5. Install Dependencies
echo [*] Checking dependencies...
pip install -r backend\requirements.txt --quiet
if !errorlevel! neq 0 (
    echo [WARNING] Retrying dependency check...
    pip install -r backend\requirements.txt
)

:: 6. Launch Standalone PC Desktop Window once server is verified listening
start "" powershell -NoProfile -Command "$client = New-Object System.Net.Sockets.TcpClient; for ($i=0; $i -lt 40; $i++) { try { $client.Connect('127.0.0.1', 8000); $client.Close(); $e86 = \"${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe\"; $e64 = \"${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe\"; $prof = \"$env:LOCALAPPDATA\OmniDownloader\AppShellProfile\"; $args = @('--app=http://localhost:8000', '--window-size=1120,820', '--app-id=OmniDownloader', \"--user-data-dir=$prof\", '--no-first-run', '--no-default-browser-check', '--disable-sync', '--disable-features=Sync,Signin,EdgeIdentitySignIn'); if (Test-Path $e86) { Start-Process $e86 -ArgumentList $args } elseif (Test-Path $e64) { Start-Process $e64 -ArgumentList $args } else { Start-Process 'http://localhost:8000' }; break } catch { Start-Sleep -Milliseconds 500 } }"

:: 7. Start FastAPI Application
echo.
echo ========================================================
echo   Server running at: http://localhost:8000
echo   Downloads saved to: %~dp0downloads
echo   Press CTRL+C in this window to stop the server.
echo ========================================================
echo.

cd backend
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload

pause
