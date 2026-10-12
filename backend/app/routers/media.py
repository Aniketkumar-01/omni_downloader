"""
Media extraction and metadata endpoints for single and batch URLs.
"""

import asyncio
import logging
from typing import List
from fastapi import APIRouter, HTTPException

from app.models import (
    InfoRequest,
    BatchInfoRequest,
    VideoItem,
    MediaInfoResponse
)
from app.downloader import extract_media_info, format_size_bytes
from app.task_manager import task_manager

logger = logging.getLogger("OmniDownloader.MediaRouter")
router = APIRouter(prefix="/api", tags=["Media"])


@router.post("/info", response_model=MediaInfoResponse)
async def get_media_info(request: InfoRequest):
    """Fetches title, thumbnails, format options, and playlist entries."""
    loop = asyncio.get_running_loop()
    cookie_browser_val = request.cookie_browser.value if request.cookie_browser else None
    info = await loop.run_in_executor(
        task_manager.executor,
        extract_media_info,
        request.url,
        cookie_browser_val,
        True
    )
    return info


@router.post("/batch/info", response_model=MediaInfoResponse)
async def get_batch_media_info(request: BatchInfoRequest):
    """
    Extracts metadata concurrently for a batch of URLs and returns an aggregated MediaInfoResponse.
    """
    loop = asyncio.get_running_loop()
    cookie_browser_val = request.cookie_browser.value if request.cookie_browser else None

    # Run extraction for all URLs concurrently in worker pool
    tasks = [
        loop.run_in_executor(
            task_manager.executor,
            extract_media_info,
            url,
            cookie_browser_val,
            True
        )
        for url in request.urls
    ]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    items: List[VideoItem] = []
    total_bytes = 0
    thumbnails = []

    for idx, res in enumerate(results):
        if isinstance(res, Exception):
            logger.warning(f"Batch item {request.urls[idx]} extraction note: {res}")
            items.append(
                VideoItem(
                    id=str(idx + 1),
                    title=f"Item {idx + 1} ({request.urls[idx][:50]}...)",
                    url=request.urls[idx],
                    filesize_formatted="Unknown"
                )
            )
            continue

        if res.is_playlist and res.items:
            for sub_item in res.items:
                items.append(sub_item)
                if sub_item.filesize:
                    total_bytes += sub_item.filesize
                if sub_item.thumbnail and len(thumbnails) < 5:
                    thumbnails.append(sub_item.thumbnail)
        else:
            for item in res.items:
                items.append(item)
                if item.filesize:
                    total_bytes += item.filesize
                if item.thumbnail and len(thumbnails) < 5:
                    thumbnails.append(item.thumbnail)

    if not items:
        raise HTTPException(status_code=400, detail="Could not retrieve media info for any of the provided URLs.")

    first_thumb = thumbnails[0] if thumbnails else None

    batch_quality_sizes = {}
    batch_quality_sizes_formatted = {}
    if total_bytes > 0:
        batch_quality_sizes = {
            "best": total_bytes,
            "1080p": total_bytes,
            "720p": int(total_bytes * 0.65),
            "480p": int(total_bytes * 0.35),
            "audio_mp3": int(total_bytes * 0.15),
        }
        batch_quality_sizes_formatted = {
            k: f"~{format_size_bytes(v)}" for k, v in batch_quality_sizes.items()
        }

    return MediaInfoResponse(
        url=request.urls[0],
        is_playlist=True,
        title=f"Batch Download ({len(items)} Items)",
        thumbnail=first_thumb,
        channel="Batch Collection",
        platform="batch",
        item_count=len(items),
        items=items,
        available_qualities=["best", "1080p", "720p", "480p", "audio_mp3"],
        quality_sizes=batch_quality_sizes,
        quality_sizes_formatted=batch_quality_sizes_formatted,
        estimated_filesize=total_bytes or None,
        filesize_formatted=f"~{format_size_bytes(total_bytes)}" if total_bytes else None
    )
