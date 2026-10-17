"""
Application and yt-dlp core updater endpoints.
"""

import os
import sys
import json
import shutil
import asyncio
import logging
import zipfile
import io
import urllib.request
import importlib
import subprocess
from pathlib import Path

from fastapi import APIRouter, HTTPException

from app.config import (
    APP_VERSION,
    GITHUB_REPO,
    USER_DATA_DIR,
    ENGINE_DIR
)
from app.core.security import NO_WINDOW_FLAG
from app.models import (
    UpdateDownloadRequest,
    UpdateApplyRequest
)
from app.updater import updater

logger = logging.getLogger("OmniDownloader.UpdaterRouter")
router = APIRouter(prefix="/api", tags=["Updater"])


@router.get("/engine/version")
async def get_engine_version():
    """Returns currently loaded yt-dlp version."""
    import yt_dlp
    return {
        "installed_version": getattr(yt_dlp.version, "__version__", "unknown"),
        "engine_dir": str(ENGINE_DIR)
    }


@router.post("/engine/update")
async def update_engine():
    """
    Installs/updates yt-dlp into isolated user data engine directory.
    Supports both standard pip installation and pure-python PyPI wheel extraction for frozen EXE.
    """
    import yt_dlp

    prev_version = getattr(yt_dlp.version, "__version__", "unknown")
    backup_dir = USER_DATA_DIR / "engine_backup"

    try:
        if backup_dir.exists():
            shutil.rmtree(backup_dir)
        if ENGINE_DIR.exists():
            shutil.copytree(ENGINE_DIR, backup_dir)

        updated = False
        err_msg = ""

        # Method 1: Try pip if not frozen
        if not getattr(sys, 'frozen', False):
            try:
                cmd = [
                    sys.executable, "-m", "pip", "install", "-U", "yt-dlp",
                    "--target", str(ENGINE_DIR),
                    "--no-warn-script-location"
                ]
                proc = await asyncio.to_thread(subprocess.run, cmd, capture_output=True, text=True, creationflags=NO_WINDOW_FLAG)
                if proc.returncode == 0:
                    updated = True
                else:
                    err_msg = proc.stderr or proc.stdout
            except Exception as pip_err:
                err_msg = str(pip_err)

        # Method 2: Direct PyPI Wheel download (works in frozen EXE and without pip)
        if not updated:
            def _download_and_extract_wheel():
                req = urllib.request.Request("https://pypi.org/pypi/yt-dlp/json", headers={"User-Agent": f"OmniDownloader/{APP_VERSION}"})
                with urllib.request.urlopen(req, timeout=15) as r:
                    pypi_data = json.loads(r.read().decode("utf-8"))
                urls = pypi_data.get("urls", [])
                whl_url = None
                for entry in urls:
                    fn = entry.get("filename", "")
                    if fn.endswith(".whl") and "py3-none-any" in fn:
                        whl_url = entry.get("url")
                        break
                if not whl_url and urls:
                    whl_url = urls[0].get("url")
                if not whl_url:
                    raise RuntimeError(f"Could not locate yt-dlp wheel package. Pip note: {err_msg}")

                dl_req = urllib.request.Request(whl_url, headers={"User-Agent": f"OmniDownloader/{APP_VERSION}"})
                with urllib.request.urlopen(dl_req, timeout=30) as whl_r:
                    raw_bytes = whl_r.read()

                with zipfile.ZipFile(io.BytesIO(raw_bytes)) as z:
                    z.extractall(str(ENGINE_DIR))

            await asyncio.to_thread(_download_and_extract_wheel)

        # Invalidate module cache and reload
        importlib.invalidate_caches()
        if str(ENGINE_DIR) not in sys.path:
            sys.path.insert(0, str(ENGINE_DIR))
        importlib.reload(yt_dlp)
        new_version = getattr(yt_dlp.version, "__version__", "unknown")

        return {
            "status": "success",
            "previous_version": prev_version,
            "current_version": new_version
        }
    except Exception as e:
        logger.error(f"Engine update failed, rolling back: {e}")
        if backup_dir.exists():
            if ENGINE_DIR.exists():
                shutil.rmtree(ENGINE_DIR)
            shutil.copytree(backup_dir, ENGINE_DIR)
        raise HTTPException(
            status_code=500,
            detail=f"Engine update failed: {str(e)}. Previous version {prev_version} retained."
        )
    finally:
        if backup_dir.exists():
            try:
                shutil.rmtree(backup_dir)
            except Exception:
                pass


@router.get("/app/version")
async def get_app_version():
    """Returns current OmniDownloader application version and repository."""
    return {
        "version": APP_VERSION,
        "repository": GITHUB_REPO
    }


@router.get("/app/update/check")
async def check_app_update(force: bool = False):
    """
    Checks GitHub Releases for new OmniDownloader versions.
    Supports force parameter to bypass cache.
    """
    result = await asyncio.to_thread(updater.check_for_updates, force=force)
    return result


@router.post("/app/update/download")
async def start_app_update_download(req: UpdateDownloadRequest):
    """Initiates streaming download of update installer in background."""
    from urllib.parse import urlparse
    parsed = urlparse(req.download_url)
    allowed_hosts = ("github.com", "objects.githubusercontent.com", "api.github.com")
    if parsed.scheme.lower() != "https" or parsed.netloc.lower() not in allowed_hosts:
        raise HTTPException(
            status_code=400,
            detail="Update URL must be an authorized HTTPS GitHub release URL."
        )

    result = await asyncio.to_thread(
        updater.start_download,
        download_url=req.download_url,
        expected_size=req.expected_size or 0,
        version_tag=req.version_tag or "latest"
    )
    return result


@router.get("/app/update/download-progress")
async def get_app_update_progress():
    """Returns current status and telemetry of the update download."""
    return updater.get_status()


@router.post("/app/update/cancel")
async def cancel_app_update():
    """Cancels active update download."""
    return updater.cancel_download()


@router.post("/app/update/apply")
async def apply_app_update(req: UpdateApplyRequest):
    """
    Executes the Inno Setup installer and initiates clean application restart.
    """
    try:
        result = updater.apply_update(
            installer_path=req.installer_path,
            silent=req.silent
        )
        return result
    except Exception as e:
        logger.error(f"Failed to apply update: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
