"""
Download lifecycle, progress streaming (SSE), and filesystem interaction endpoints.
"""

import os
import sys
import subprocess
import logging
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from sse_starlette.sse import EventSourceResponse

from app.config import DOWNLOADS_DIR, get_default_download_dir
from app.core.security import NO_WINDOW_FLAG
from app.models import DownloadRequest, DownloadTaskStatus
from app.task_manager import task_manager
from app.utils.filesystem import is_safe_subpath

logger = logging.getLogger("OmniDownloader.DownloadsRouter")
router = APIRouter(prefix="/api", tags=["Downloads"])


@router.post("/download")
async def start_download(request: DownloadRequest):
    """Initiates a background download task."""
    task_id = task_manager.create_task(request)
    task_manager.start_download(task_id, request)
    return {
        "task_id": task_id,
        "status": "queued",
        "message": "Download task queued successfully."
    }


@router.get("/progress/{task_id}")
async def get_download_progress(task_id: str):
    """Server-Sent Events endpoint streaming real-time download progress."""
    task = task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return EventSourceResponse(task_manager.subscribe(task_id))


@router.get("/status/{task_id}", response_model=DownloadTaskStatus)
async def get_task_status(task_id: str):
    """Status endpoint for polling fallback."""
    task = task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return task


@router.post("/tasks/{task_id}/cancel")
async def cancel_download_task(task_id: str):
    """Cancels an active download task."""
    success = task_manager.cancel_task(task_id)
    task = task_manager.get_task(task_id)
    if not success and not task:
        raise HTTPException(status_code=404, detail="Task not found.")
    return {
        "status": "success",
        "task_id": task_id,
        "message": "Task cancellation requested.",
        "state": task.status if task else "cancelled"
    }


@router.post("/tasks/{task_id}/pause")
async def pause_download_task(task_id: str):
    """Pauses an active download task."""
    success = task_manager.pause_task(task_id)
    task = task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found.")
    return {
        "status": "success" if success else "failed",
        "task_id": task_id,
        "state": task.status
    }


@router.post("/tasks/{task_id}/resume")
async def resume_download_task(task_id: str):
    """Resumes a paused download task."""
    success = task_manager.resume_task(task_id)
    task = task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found.")
    return {
        "status": "success" if success else "failed",
        "task_id": task_id,
        "state": task.status
    }


@router.get("/file/{task_id}")
async def download_file_browser(task_id: str):
    """
    Directly serves the downloaded file as a browser attachment.
    Enforces strict path traversal checks against DOWNLOADS_DIR.
    """
    task = task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")

    if task.status != "completed" or not task.output_files:
        raise HTTPException(status_code=400, detail="File is not yet ready or download failed")

    file_path = Path(task.output_files[0]).resolve()
    if not file_path.exists() or not file_path.is_file():
        raise HTTPException(status_code=404, detail="File not found on server disk")

    # Strict path traversal validation
    allowed_dirs = [DOWNLOADS_DIR.resolve(), get_default_download_dir().resolve()]
    if hasattr(task, "download_dir") and task.download_dir:
        allowed_dirs.append(Path(task.download_dir).resolve())

    if not is_safe_subpath(file_path, allowed_dirs):
        raise HTTPException(status_code=403, detail="Access denied: path is outside downloads directory.")

    return FileResponse(
        path=str(file_path),
        filename=file_path.name,
        media_type="application/octet-stream"
    )


@router.post("/open-folder")
async def open_downloads_folder(task_id: Optional[str] = None):
    """
    Opens Windows Explorer highlighting the downloaded file, or opening the download folder.
    """
    try:
        current_dir = get_default_download_dir().resolve()
        target_path = None

        if task_id:
            task = task_manager.get_task(task_id)
            if task:
                # 1. Output files from task
                if task.output_files:
                    for f in task.output_files:
                        cand = Path(f).resolve()
                        if cand.exists() and cand.is_file():
                            target_path = cand
                            current_dir = cand.parent
                            break
                        elif cand.parent.exists():
                            target_path = cand.parent
                            current_dir = cand.parent
                            break

                # Path traversal check on requested task path
                if target_path:
                    allowed_dirs = [DOWNLOADS_DIR.resolve(), get_default_download_dir().resolve()]
                    if hasattr(task, "download_dir") and task.download_dir:
                        allowed_dirs.append(Path(task.download_dir).resolve())
                    if not is_safe_subpath(target_path, allowed_dirs):
                        raise HTTPException(status_code=403, detail="Access denied: path is outside downloads directory.")

                # 2. Task recorded download_dir
                if not target_path and hasattr(task, "download_dir") and task.download_dir:
                    t_dir = Path(task.download_dir).resolve()
                    if t_dir.exists():
                        target_path = t_dir
                        current_dir = t_dir

        if not target_path:
            target_path = current_dir

        # 3. If target_path is a directory, look for recently downloaded media in it
        if target_path.is_dir() and target_path.exists():
            valid_exts = ('.mp4', '.mkv', '.webm', '.mp3', '.m4a', '.wav', '.opus', '.flac')
            candidates = [f for f in target_path.iterdir() if f.is_file() and f.suffix.lower() in valid_exts]
            if candidates:
                candidates.sort(key=lambda x: x.stat().st_mtime, reverse=True)
                target_path = candidates[0]

        if not target_path.exists():
            target_path = current_dir
            target_path.mkdir(parents=True, exist_ok=True)

        norm_path = os.path.normpath(str(target_path))
        logger.info(f"Opening in Explorer: {norm_path}")

        if os.name == 'nt':
            if target_path.is_file():
                subprocess.Popen(["explorer.exe", f"/select,{norm_path}"], creationflags=NO_WINDOW_FLAG)
            else:
                subprocess.Popen(["explorer.exe", norm_path], creationflags=NO_WINDOW_FLAG)
        elif sys.platform == 'darwin':
            if target_path.is_file():
                subprocess.Popen(["open", "-R", norm_path])
            else:
                subprocess.Popen(["open", norm_path])
        else:
            folder = norm_path if target_path.is_dir() else str(target_path.parent)
            subprocess.Popen(["xdg-open", folder])

        return {"status": "success", "opened_path": norm_path}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error opening folder: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Failed to open downloads folder.")


@router.post("/open-file")
async def open_downloaded_file(task_id: str):
    """
    Directly opens/plays the downloaded file in the user's default system player with guaranteed resolution.
    """
    task = task_manager.get_task(task_id)
    file_to_open: Optional[Path] = None

    if task and task.output_files:
        for f in task.output_files:
            cand = Path(f).resolve()
            if cand.exists() and cand.is_file():
                file_to_open = cand
                break

    # Robust fallback: look in task's download dir or default download dir for newest media file
    if not file_to_open:
        valid_exts = ('.mp4', '.mkv', '.webm', '.mp3', '.m4a', '.wav', '.opus', '.flac')
        search_dirs = [get_default_download_dir().resolve()]
        if task and hasattr(task, "download_dir") and task.download_dir:
            search_dirs.insert(0, Path(task.download_dir).resolve())

        for sdir in search_dirs:
            if sdir.exists() and sdir.is_dir():
                candidates = [f for f in sdir.iterdir() if f.is_file() and f.suffix.lower() in valid_exts]
                if candidates:
                    candidates.sort(key=lambda x: x.stat().st_mtime, reverse=True)
                    file_to_open = candidates[0]
                    break

    if not file_to_open or not file_to_open.exists():
        raise HTTPException(status_code=404, detail="Downloaded media file could not be located on disk.")

    norm_path = os.path.normpath(str(file_to_open))
    logger.info(f"Launching media file: {norm_path}")
    try:
        if os.name == 'nt':
            try:
                os.startfile(norm_path)
            except Exception as e:
                logger.warning(f"os.startfile failed ({e}), attempting fallback: {norm_path}")
                subprocess.Popen(["explorer.exe", norm_path], creationflags=NO_WINDOW_FLAG)
        elif sys.platform == 'darwin':
            subprocess.Popen(["open", norm_path])
        else:
            subprocess.Popen(["xdg-open", norm_path])
        return {"status": "success", "file": norm_path}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error launching file {norm_path}: {e}")
        raise HTTPException(status_code=500, detail="Failed to launch downloaded media file.")
