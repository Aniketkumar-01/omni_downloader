"""
Filesystem and path utilities for OmniDownloader.
Contains Windows-safe path sanitization, junction handling, and filename cleansing.
"""

import os
import re
from pathlib import Path
from typing import Optional

# Windows device reserved names that cause OS errors if used as filenames
WINDOWS_RESERVED_NAMES = {
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
}

def sanitize_filename(title: Optional[str], max_length: int = 150) -> str:
    """
    Sanitizes filenames and folder names for cross-platform and Windows filesystem safety.
    Strips illegal characters (< > : " / \\ | ? *), handles reserved device names (CON, PRN),
    and enforces maximum character length bounds.
    """
    if not title:
        return "untitled_video"

    # Replace illegal characters on Windows: < > : " / \ | ? * with underscore
    sanitized = re.sub(r'[<>:"/\\|?*]', '_', str(title))
    sanitized = re.sub(r'[\x00-\x1f\x7f]', '', sanitized).strip('. ')

    # Handle Windows reserved device names (e.g., CON.mp4 -> _CON.mp4)
    stem = sanitized.split('.')[0].upper()
    if stem in WINDOWS_RESERVED_NAMES:
        sanitized = f"_{sanitized}"

    if len(sanitized) > max_length:
        sanitized = sanitized[:max_length].rstrip('. ')

    return sanitized or "untitled_video"


def ensure_directory(path: Path) -> Path:
    """Guarantees a directory exists, creating parents if necessary."""
    resolved = Path(path).expanduser().resolve()
    resolved.mkdir(parents=True, exist_ok=True)
    return resolved


def is_safe_subpath(target_path: Path, allowed_directories: list[Path]) -> bool:
    """
    Checks that target_path resolves strictly within one of the allowed_directories,
    guarding against directory traversal attacks.
    """
    try:
        resolved_target = target_path.resolve()
        for base_dir in allowed_directories:
            if not base_dir.exists():
                continue
            resolved_base = base_dir.resolve()
            if resolved_target == resolved_base or resolved_target.is_relative_to(resolved_base):
                return True
        return False
    except Exception:
        return False
