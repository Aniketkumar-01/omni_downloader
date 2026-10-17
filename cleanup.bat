@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   OmniDownloader - Workspace Artifacts Cleaner
echo ========================================================
echo.

:: 1. Remove temporary commit message & preview mockup files
if exist ".commit_msg" (
    echo [*] Removing leftover .commit_msg...
    del /f /q ".commit_msg" 2>nul
)

if exist "preview.html" (
    echo [*] Removing redundant preview.html...
    del /f /q "preview.html" 2>nul
)

if exist "Run_OmniDownloader.bat" (
    echo [*] Removing duplicate Run_OmniDownloader.bat (use start.bat)...
    del /f /q "Run_OmniDownloader.bat" 2>nul
)

:: 2. Remove intermediate PyInstaller build directories
if exist "build" (
    echo [*] Cleaning intermediate build\ directory...
    rmdir /s /q "build" 2>nul
)

if exist "dist" (
    :: Only delete dist if empty
    dir /b /a "dist" 2>nul | findstr "." >nul || (
        echo [*] Cleaning empty dist\ directory...
        rmdir /s /q "dist" 2>nul
    )
)

:: 3. Clean Python bytecode caches
echo [*] Cleaning __pycache__ and pytest caches...
for /d /r . %%d in (__pycache__) do @if exist "%%d" rmdir /s /q "%%d" 2>nul
for /d /r . %%d in (.pytest_cache) do @if exist "%%d" rmdir /s /q "%%d" 2>nul

echo.
echo ========================================================
echo   Workspace is clean and streamlined!
echo ========================================================
echo.
pause
