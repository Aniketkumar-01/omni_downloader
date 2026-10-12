import os
import sys
import re
import json
import logging
from pathlib import Path

logger = logging.getLogger("OmniDownloader.Config")

# --------------------------------------------------------------------------
# Windows Mount Point and Symlink Hardening (WinError 448 Mitigation)
# --------------------------------------------------------------------------
_orig_realpath = getattr(os.path, '_orig_realpath', os.path.realpath)
os.path._orig_realpath = _orig_realpath

def _safe_realpath(path, *args, **kwargs):
    """
    Hardened wrapper around os.path.realpath that prevents crashes on Windows
    when encountering untrusted reparse points / junctions (WinError 448:
    ERROR_UNTRUSTED_MOUNT_POINT) or corrupted symlinks.
    """
    try:
        return _orig_realpath(path, *args, **kwargs)
    except OSError:
        try:
            return os.path.abspath(path)
        except Exception:
            return str(path)

os.path.realpath = _safe_realpath

def sanitize_system_path():
    """
    Inspects os.environ['PATH'] and replaces or removes untrusted junction/mount
    points (such as NVM for Windows .nodejs) that trigger WinError 448 during
    file resolution or subprocess execution.
    """
    if os.name != 'nt':
        return

    path_env = os.environ.get("PATH", "")
    if not path_env:
        return

    cleaned = []
    seen = set()
    for entry in path_env.split(os.pathsep):
        entry_clean = entry.strip().strip('"\'')
        if not entry_clean:
            continue

        normed = os.path.normcase(entry_clean)
        if normed in seen:
            continue
        seen.add(normed)

        # Test if the path triggers WinError 448 (ERROR_UNTRUSTED_MOUNT_POINT)
        try:
            _orig_realpath(entry_clean)
            cleaned.append(entry_clean)
        except OSError as e:
            # Untrusted mount point or unresolvable reparse point
            replacement = None
            try:
                target = os.readlink(entry_clean)
                if not os.path.isabs(target):
                    target = os.path.join(os.path.dirname(entry_clean), target)
                if os.path.exists(target):
                    replacement = target
            except Exception:
                replacement = None

            if replacement:
                logger.info(
                    f"Resolved untrusted junction in PATH '{entry_clean}' to target '{replacement}'"
                )
                cleaned.append(replacement)
            else:
                logger.warning(
                    f"Excluding untrusted/broken mount point from PATH environment: '{entry_clean}' ({e})"
                )

    os.environ["PATH"] = os.pathsep.join(cleaned)

sanitize_system_path()

# Defensively patch yt_dlp's JS runtime exe finder if yt_dlp is installed
try:
    import yt_dlp.utils._jsruntime as _jsruntime
    _orig_find_exe = getattr(_jsruntime, '_orig_find_exe', _jsruntime._find_exe)
    _jsruntime._orig_find_exe = _orig_find_exe

    def _safe_find_exe(basename: str) -> str:
        try:
            return _orig_find_exe(basename)
        except OSError:
            return basename

    _jsruntime._find_exe = _safe_find_exe
except Exception:
    pass

# --------------------------------------------------------------------------
# Environment Configuration (.env support)
# --------------------------------------------------------------------------
def _load_env_file(env_file_path: Path):
    """Loads key-value pairs from a .env file into os.environ if not already set."""
    if not env_file_path.is_file():
        return
    try:
        with open(env_file_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, val = line.split("=", 1)
                key = key.strip()
                val = val.strip().strip("'\"")
                if key and key not in os.environ:
                    os.environ[key] = val
    except Exception as e:
        logger.debug(f".env file read note: {e}")

# Paths with PyInstaller bundle support
if getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS'):
    BASE_DIR = Path(sys._MEIPASS).resolve()
    FRONTEND_DIR = BASE_DIR / "frontend"
    BACKEND_DIR = BASE_DIR / "backend"
    DOWNLOADS_DIR = Path.home() / "Downloads"
else:
    BASE_DIR = Path(__file__).resolve().parent.parent.parent
    BACKEND_DIR = Path(__file__).resolve().parent.parent
    FRONTEND_DIR = BASE_DIR / "frontend"
    DOWNLOADS_DIR = BASE_DIR / "downloads"

# Load root .env file if present
_load_env_file(BASE_DIR / ".env")

# Re-export filesystem utilities for backwards compatibility
from app.utils.filesystem import sanitize_filename, WINDOWS_RESERVED_NAMES

# User Data Directory (platformdirs with robust fallback)
try:
    import platformdirs
    USER_DATA_DIR = Path(platformdirs.user_data_dir("OmniDownloader", appauthor=False))
except Exception:
    if os.name == 'nt':
        appdata = os.environ.get("LOCALAPPDATA") or os.environ.get("APPDATA")
        USER_DATA_DIR = Path(appdata) / "OmniDownloader" if appdata else Path.home() / ".omnidownloader"
    else:
        USER_DATA_DIR = Path.home() / ".local" / "share" / "OmniDownloader"

USER_DATA_DIR.mkdir(parents=True, exist_ok=True)
DOWNLOADS_DIR.mkdir(parents=True, exist_ok=True)
SETTINGS_FILE = USER_DATA_DIR / "settings.json"

def get_default_download_dir() -> Path:
    """Returns the persistent configured download directory, defaulting to Windows User Downloads."""
    # 1. Check environment variable override
    env_dir = os.getenv("DOWNLOADS_DIR", "").strip().strip('"\'')
    if env_dir:
        p = Path(env_dir).resolve()
        p.mkdir(parents=True, exist_ok=True)
        return p

    # 2. Check settings.json custom path
    if SETTINGS_FILE.exists():
        try:
            data = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))
            custom = data.get("download_dir")
            if custom:
                p = Path(custom).resolve()
                p.mkdir(parents=True, exist_ok=True)
                return p
        except Exception:
            pass

    # 3. Default to user's real Windows Downloads folder if available
    user_downloads = Path.home() / "Downloads"
    if user_downloads.exists() and user_downloads.is_dir():
        return user_downloads
    return DOWNLOADS_DIR

def set_download_dir(path: Path) -> Path:
    """Sets and persists the user's custom download directory."""
    clean_str = str(path).strip().strip('"\'')
    resolved = Path(clean_str).expanduser().resolve()
    resolved.mkdir(parents=True, exist_ok=True)
    settings = {}
    if SETTINGS_FILE.exists():
        try:
            settings = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))
        except Exception:
            settings = {}
    settings["download_dir"] = str(resolved)
    try:
        SETTINGS_FILE.write_text(json.dumps(settings, indent=2), encoding="utf-8")
    except Exception:
        pass
    return resolved

# Engine directory for isolated yt-dlp runtime updates
ENGINE_DIR = USER_DATA_DIR / "engine"
ENGINE_DIR.mkdir(parents=True, exist_ok=True)
if str(ENGINE_DIR) not in sys.path:
    sys.path.insert(0, str(ENGINE_DIR))

# Application Version and Remote Update Settings
APP_VERSION = os.getenv("APP_VERSION", "1.4.8")
GITHUB_REPO = os.getenv("GITHUB_REPO", "Aniketkumar-01/omni_downloader")
UPDATES_DIR = USER_DATA_DIR / "updates"
UPDATES_DIR.mkdir(parents=True, exist_ok=True)

# Persistent Cookie Jar in User Data directory
COOKIES_FILE = USER_DATA_DIR / "cookies.txt"

# Server Network & Security Settings (Localhost binding only)
SERVER_HOST = os.getenv("HOST", "127.0.0.1")
SERVER_PORT = int(os.getenv("PORT", "8000"))
MAX_DOWNLOAD_WORKERS = int(os.getenv("MAX_WORKERS", "2"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
MAX_COOKIE_SIZE = 2 * 1024 * 1024  # 2 MB limit

