"""
System capabilities, folder management, FFmpeg automation, and process lifetime endpoints.
"""

import os
import sys
import asyncio
import logging
import threading
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException, Request

from app.config import (
    get_default_download_dir,
    set_download_dir,
    USER_DATA_DIR
)
from app.core.security import API_TOKEN, NO_WINDOW_FLAG
from app.models import SetDownloadDirRequest

logger = logging.getLogger("OmniDownloader.SystemRouter")
router = APIRouter(prefix="/api", tags=["System"])

# Watchdog / Graceful shutdown timer
_shutdown_timer: Optional[threading.Timer] = None


def record_heartbeat():
    """Cancels any pending grace shutdown when the frontend reloads or pings."""
    global _shutdown_timer
    if _shutdown_timer is not None:
        try:
            _shutdown_timer.cancel()
        except Exception:
            pass
        _shutdown_timer = None


@router.get("/token")
async def get_session_token(request: Request):
    """Allows authorized local frontend clients to retrieve active session token."""
    fetch_site = request.headers.get("sec-fetch-site", "").strip().lower()
    if fetch_site == "cross-site":
        logger.warning(f"Rejected cross-site request for session token from {request.client.host if request.client else 'unknown'}")
        raise HTTPException(status_code=403, detail="Cross-site access denied.")
    return {"token": API_TOKEN}


@router.get("/clipboard")
async def get_clipboard_text():
    """Returns text currently on the system clipboard (Windows ctypes fallback)."""
    text = ""
    if os.name == 'nt':
        try:
            import ctypes
            CF_UNICODETEXT = 13
            user32 = ctypes.windll.user32
            kernel32 = ctypes.windll.kernel32
            if user32.OpenClipboard(None):
                try:
                    h_mem = user32.GetClipboardData(CF_UNICODETEXT)
                    if h_mem:
                        kernel32.GlobalLock.restype = ctypes.c_void_p
                        p_mem = kernel32.GlobalLock(h_mem)
                        if p_mem:
                            try:
                                text = ctypes.wstring_at(p_mem)
                            finally:
                                kernel32.GlobalUnlock(h_mem)
                finally:
                    user32.CloseClipboard()
        except Exception as e:
            logger.debug(f"Native clipboard query note: {e}")
    return {"text": text or ""}


@router.get("/download-dir")
async def get_download_directory():
    """Returns the current active download directory."""
    dir_path = get_default_download_dir()
    return {"download_dir": str(dir_path)}


@router.post("/download-dir")
async def update_download_directory(request: SetDownloadDirRequest):
    """Sets and persists a custom download directory, safely stripping surrounding quotes."""
    try:
        raw = request.download_dir.strip().strip('"\'')
        if not raw:
            raw = str(get_default_download_dir())
        new_path = Path(raw).expanduser().resolve()
        saved = set_download_dir(new_path)
        return {"status": "success", "download_dir": str(saved)}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to set download directory '{request.download_dir}': {e}")
        raise HTTPException(status_code=400, detail="Invalid directory path provided.")


@router.post("/choose-folder")
async def trigger_folder_picker():
    """
    Opens native Windows folder selection dialog and returns chosen folder.
    Guarantees exactly ONE dialog is shown without unwanted re-prompting.
    """
    curr = str(get_default_download_dir())
    if os.name != 'nt':
        return {"status": "error", "message": "Folder picker only supported on Windows.", "download_dir": curr, "path": curr}

    loop = asyncio.get_running_loop()

    def _tk_ask() -> tuple[str, Optional[str]]:
        try:
            import tkinter as tk
            from tkinter import filedialog
            root = tk.Tk()
            root.withdraw()
            root.attributes('-topmost', True)
            res = filedialog.askdirectory(
                parent=root,
                title="Select Download Destination Folder",
                initialdir=curr
            )
            root.destroy()
            if res:
                return ("OK", str(res).strip())
            return ("CANCEL", None)
        except Exception as e:
            logger.debug(f"Tkinter folder picker not available: {e}")
            return ("ERROR", None)

    try:
        status, tk_res = await loop.run_in_executor(None, _tk_ask)
        if status == "OK" and tk_res and Path(tk_res).is_dir():
            saved = set_download_dir(Path(tk_res))
            return {"status": "success", "download_dir": str(saved), "path": str(saved)}
        elif status == "CANCEL":
            return {"status": "cancelled", "download_dir": curr, "path": curr}
    except Exception as e:
        logger.debug(f"Tkinter method error: {e}")

    # Fallback to PowerShell FolderBrowserDialog only if Tkinter is missing
    ps_script = f"""
Add-Type -AssemblyName System.Windows.Forms
$f = New-Object System.Windows.Forms.FolderBrowserDialog
$f.Description = 'Select download destination folder for OmniDownloader'
$f.ShowNewFolderButton = $true
$f.SelectedPath = '{curr}'
$res = $f.ShowDialog()
if ($res -eq [System.Windows.Forms.DialogResult]::OK -and $f.SelectedPath) {{
    [Console]::Out.Write($f.SelectedPath)
}}
exit 0
"""
    try:
        import base64
        encoded = base64.b64encode(ps_script.encode('utf-16le')).decode('ascii')
        proc = await asyncio.create_subprocess_exec(
            "powershell", "-NoProfile", "-STA", "-EncodedCommand", encoded,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            creationflags=NO_WINDOW_FLAG
        )
        stdout, _ = await proc.communicate()
        chosen = stdout.decode("utf-8", errors="replace").strip().strip('"\'')
        if chosen and Path(chosen).is_dir():
            saved = set_download_dir(Path(chosen))
            return {"status": "success", "download_dir": str(saved), "path": str(saved)}
        return {"status": "cancelled", "download_dir": curr, "path": curr}
    except Exception as e:
        logger.warning(f"PowerShell folder picker error: {e}")

    return {"status": "cancelled", "download_dir": curr, "path": curr}


@router.get("/system-status")
async def get_system_status():
    """
    Returns system readiness status including FFmpeg availability.
    """
    from app.downloader import get_ffmpeg_path
    ff_path = get_ffmpeg_path()
    has_ffmpeg = bool(ff_path and Path(ff_path).is_file())

    return {
        "status": "ready" if has_ffmpeg else "warning",
        "ffmpeg_installed": has_ffmpeg,
        "ffmpeg_path": ff_path if has_ffmpeg else None,
        "recommended_winget": "winget install Gyan.FFmpeg",
        "official_download_url": "https://www.gyan.dev/ffmpeg/builds/",
        "message": (
            "FFmpeg is ready. High-resolution stream merging (1080p, 4K) and MP3 conversion are active."
            if has_ffmpeg else
            "FFmpeg is required for 1080p/4K video merging and MP3 audio conversion."
        )
    }


@router.post("/install-ffmpeg")
async def auto_install_ffmpeg():
    """
    Attempts automated 1-click installation of FFmpeg for Windows:
    1. Try running `winget install Gyan.FFmpeg`
    2. Fallback to downloading standalone binaries from official builds into USER_DATA_DIR / 'ffmpeg.exe'.
    """
    import urllib.request
    import zipfile
    import io
    from app.downloader import get_ffmpeg_path, ensure_ffmpeg_in_path

    curr = get_ffmpeg_path()
    if curr and Path(curr).is_file():
        return {"status": "success", "message": "FFmpeg is already installed and ready.", "ffmpeg_path": curr}

    if os.name == 'nt':
        try:
            logger.info("Attempting automated winget installation of Gyan.FFmpeg...")
            proc = await asyncio.create_subprocess_exec(
                "winget", "install", "Gyan.FFmpeg", "--accept-package-agreements", "--accept-source-agreements", "--silent",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                creationflags=NO_WINDOW_FLAG
            )
            try:
                await asyncio.wait_for(proc.communicate(), timeout=35.0)
                ff = get_ffmpeg_path()
                if ff and Path(ff).is_file():
                    return {"status": "success", "message": "FFmpeg installed successfully via winget!", "ffmpeg_path": ff}
            except asyncio.TimeoutError:
                try:
                    proc.kill()
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"Winget install attempt note: {e}")

    # Fallback to direct standalone binary download
    target_exe = USER_DATA_DIR / "ffmpeg.exe"
    download_urls = [
        "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
        "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
    ]

    loop = asyncio.get_running_loop()

    def _download_and_extract():
        for url in download_urls:
            try:
                logger.info(f"Downloading FFmpeg from {url}...")
                req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
                with urllib.request.urlopen(req, timeout=60) as resp:
                    data = resp.read()

                with zipfile.ZipFile(io.BytesIO(data)) as z:
                    for filename in z.namelist():
                        if filename.endswith("ffmpeg.exe"):
                            with z.open(filename) as src, open(target_exe, "wb") as dst:
                                dst.write(src.read())
                            ensure_ffmpeg_in_path(str(target_exe))
                            return str(target_exe)
            except Exception as ex:
                logger.warning(f"Download attempt from {url} failed: {ex}")
        return None

    try:
        installed = await loop.run_in_executor(None, _download_and_extract)
        if installed and Path(installed).is_file():
            return {
                "status": "success",
                "message": "FFmpeg successfully installed and registered!",
                "ffmpeg_path": installed
            }
    except Exception as e:
        logger.error(f"Automated FFmpeg download failed: {e}")

    raise HTTPException(
        status_code=500,
        detail="Automated install failed. Please open PowerShell and run 'winget install Gyan.FFmpeg' or visit https://www.gyan.dev/ffmpeg/builds/"
    )


@router.api_route("/heartbeat", methods=["GET", "POST"])
async def api_heartbeat():
    """Receives periodic frontend ping to keep background process alive."""
    record_heartbeat()
    return {"status": "alive"}


@router.api_route("/shutdown", methods=["GET", "POST"])
async def api_shutdown():
    """
    Called when frontend window unloads or closes.
    Schedules a 15-second grace shutdown. If the user was refreshing (F5),
    the reloaded page sends a heartbeat and cancels this timer.
    """
    global _shutdown_timer
    logger.info("Received window unload notification. Scheduling 15s grace termination...")
    if _shutdown_timer is not None:
        try:
            _shutdown_timer.cancel()
        except Exception:
            pass

    def _do_grace_exit():
        from app.task_manager import task_manager
        has_active = any(
            t.status not in ("completed", "failed", "cancelled")
            for t in getattr(task_manager, "tasks", {}).values()
        )
        if has_active:
            logger.info("Active download in progress; deferring auto-shutdown.")
            return
        logger.info("Window closed and no reconnection within 15s grace period. Terminating process cleanly.")
        os._exit(0)

    _shutdown_timer = threading.Timer(15.0, _do_grace_exit)
    _shutdown_timer.daemon = True
    _shutdown_timer.start()
    return {"status": "scheduled", "grace_seconds": 15}
