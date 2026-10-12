"""
Cookie management and Netscape cookie file validation endpoints.
"""

import logging
from fastapi import APIRouter, HTTPException, Request

from app.config import COOKIES_FILE, MAX_COOKIE_SIZE

logger = logging.getLogger("OmniDownloader.CookiesRouter")
router = APIRouter(prefix="/api", tags=["Cookies"])


def is_valid_netscape_cookie_file(content: bytes) -> bool:
    """Validates that bytes match Netscape cookie file standards."""
    try:
        text = content.decode("utf-8", errors="replace")
    except Exception:
        return False

    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if not lines:
        return False

    if any(line.startswith("# Netscape HTTP Cookie File") or line.startswith("# HTTP Cookie File") for line in lines[:5]):
        return True

    valid_records = 0
    for line in lines:
        if line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) == 7:
            valid_records += 1

    return valid_records > 0


@router.get("/cookies-status")
async def get_cookies_status():
    """Returns whether cookies.txt is currently installed and active."""
    exists = COOKIES_FILE.exists()
    return {
        "has_cookies_file": exists,
        "size_bytes": COOKIES_FILE.stat().st_size if exists else 0,
        "path": str(COOKIES_FILE) if exists else None
    }


@router.post("/upload-cookies")
async def upload_cookies(request: Request):
    """
    Validates and saves cookies.txt into user data directory.
    Enforces 2 MB size cap and Netscape format validation.
    """
    content = await request.body()
    if not content:
        raise HTTPException(status_code=400, detail="Uploaded cookie file is empty.")

    if len(content) > MAX_COOKIE_SIZE:
        raise HTTPException(
            status_code=413,
            detail=f"Cookie file exceeds maximum allowed size of {MAX_COOKIE_SIZE // (1024*1024)} MB."
        )

    if not is_valid_netscape_cookie_file(content):
        raise HTTPException(
            status_code=400,
            detail="Invalid cookie format. File must be Netscape format (# Netscape HTTP Cookie File or 7 tab-separated fields)."
        )

    COOKIES_FILE.write_bytes(content)
    logger.info(f"Saved cookies.txt to {COOKIES_FILE} ({len(content)} bytes)")
    return {"status": "success", "message": "cookies.txt successfully loaded and active!"}


@router.delete("/cookies")
async def delete_cookies():
    """Deletes existing cookies.txt file from user data directory."""
    if COOKIES_FILE.exists():
        COOKIES_FILE.unlink()
        logger.info(f"Deleted {COOKIES_FILE}")
    return {"status": "success", "message": "cookies.txt removed."}
