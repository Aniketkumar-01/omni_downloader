"""
OmniDownloader - Modular FastAPI Application Entrypoint
"""

import os
import re
import shutil
import asyncio
import logging
import secrets
from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from app.config import (
    BASE_DIR,
    FRONTEND_DIR,
    SERVER_HOST,
    SERVER_PORT,
    APP_VERSION,
    MAX_COOKIE_SIZE,
    LOG_LEVEL
)
from app.core.security import API_TOKEN
from app.downloader import MediaExtractionError, map_ytdlp_error, get_ffmpeg_path
from app.task_manager import task_manager
from app.routers import (
    media_router,
    downloads_router,
    system_router,
    cookies_router,
    updater_router
)
from app.routers.system import record_heartbeat

# Configure application logging
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger("OmniDownloader.Main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    loop = asyncio.get_running_loop()
    task_manager.set_loop(loop)

    ff = get_ffmpeg_path()
    if ff:
        logger.info(f"FFmpeg binary detected and ready at: {ff}")
    else:
        logger.warning("No FFmpeg binary found! High-res (1080p/720p) stream merging will be unavailable.")

    logger.info(f"FastAPI Server started on {SERVER_HOST}:{SERVER_PORT} with TaskManager initialized.")
    yield
    logger.info("FastAPI Server shutting down.")


app = FastAPI(
    title="OmniDownloader Web App",
    description="Hardened universal media downloader with modular routers and real-time SSE progress",
    version=APP_VERSION,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
    lifespan=lifespan
)


# --------------------------------------------------------------------------
# Security & Auth Token Middleware
# --------------------------------------------------------------------------
@app.middleware("http")
async def security_and_auth_middleware(request: Request, call_next):
    # 1. Host header validation: allow only 127.0.0.1, localhost, or testserver
    host = request.headers.get("host", "").strip().lower()
    valid_host = bool(re.match(r"^(127\.0\.0\.1|localhost|testserver)(:\d+)?$", host))
    if not valid_host:
        return JSONResponse(
            status_code=403,
            content={
                "code": "FORBIDDEN",
                "message": f"Rejected request with unauthorized Host header: '{host}'",
                "hint": "Requests must be addressed to 127.0.0.1 or localhost."
            }
        )

    # 2. Origin validation: if Origin header is present, ensure it matches our origin
    origin = request.headers.get("origin")
    if origin:
        origin_clean = origin.strip().lower()
        allowed_origins = {
            f"http://127.0.0.1:{SERVER_PORT}",
            f"http://localhost:{SERVER_PORT}",
            "http://127.0.0.1",
            "http://localhost",
        }
        if origin_clean not in allowed_origins:
            return JSONResponse(
                status_code=403,
                content={
                    "code": "FORBIDDEN",
                    "message": f"Cross-origin request from '{origin}' rejected.",
                    "hint": "API requests must originate from the local application.",
                    "source": "app"
                }
            )

    # 3. Require X-Auth-Token on /api/* requests (except public bootstrap endpoints)
    public_endpoints = ("/api/token", "/api/heartbeat")
    if request.url.path.startswith("/api/") and request.url.path not in public_endpoints:
        token = request.headers.get("x-auth-token") or request.query_params.get("token")
        if not token or not secrets.compare_digest(token, API_TOKEN):
            return JSONResponse(
                status_code=401,
                content={
                    "code": "UNAUTHORIZED",
                    "message": "Missing or invalid authentication token.",
                    "hint": "Provide valid token in X-Auth-Token header or ?token= query parameter.",
                    "source": "app"
                }
            )

    record_heartbeat()
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    path = request.url.path
    if any(path.endswith(ext) for ext in (".css", ".js", ".svg", ".png", ".jpg", ".ico", ".woff", ".woff2")):
        response.headers["Cache-Control"] = "public, max-age=3600"
    return response


# --------------------------------------------------------------------------
# Standardized Error Shape Exception Handlers
# --------------------------------------------------------------------------
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    code = "HTTP_ERROR"
    hint = "Verify your request parameters and try again."
    source = "app"
    if exc.status_code == 401:
        code = "UNAUTHORIZED"
        hint = "Provide valid token in X-Auth-Token header or ?token= query parameter."
    elif exc.status_code == 403:
        code = "FORBIDDEN"
        hint = "Action forbidden: resource is restricted or path is outside downloads directory."
    elif exc.status_code == 404:
        code = "NOT_FOUND"
        hint = "The requested resource or task does not exist."
    elif exc.status_code == 413:
        code = "PAYLOAD_TOO_LARGE"
        hint = f"Uploaded content exceeds size limit ({MAX_COOKIE_SIZE // (1024*1024)} MB)."
    elif exc.status_code == 422:
        code = "VALIDATION_ERROR"
        hint = "Ensure all request parameters conform to required schema."

    return JSONResponse(
        status_code=exc.status_code,
        content={"code": code, "message": str(exc.detail), "hint": hint, "source": source}
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    errors = exc.errors()
    msg = errors[0].get("msg", "Validation error") if errors else "Invalid request data"
    loc = " -> ".join(str(l) for l in errors[0].get("loc", [])) if errors else ""
    return JSONResponse(
        status_code=422,
        content={
            "code": "VALIDATION_ERROR",
            "message": f"{loc}: {msg}" if loc else msg,
            "hint": "Check field types, values, and allowable choices.",
            "source": "app"
        }
    )


@app.exception_handler(MediaExtractionError)
async def media_extraction_handler(request: Request, exc: MediaExtractionError):
    return JSONResponse(
        status_code=400,
        content={
            "code": exc.detail.code,
            "message": exc.detail.message,
            "hint": exc.detail.hint,
            "source": exc.detail.source
        }
    )


@app.exception_handler(Exception)
async def general_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception on {request.url.path}: {exc}", exc_info=True)
    detail = map_ytdlp_error(exc)
    return JSONResponse(
        status_code=500,
        content={
            "code": detail.code,
            "message": detail.message,
            "hint": detail.hint,
            "source": detail.source
        }
    )


# --------------------------------------------------------------------------
# Root HTML Service with Auth Token Injection
# --------------------------------------------------------------------------
@app.get("/", response_class=HTMLResponse)
@app.get("/index.html", response_class=HTMLResponse)
async def serve_index():
    """Serves frontend index.html with authentication token injected."""
    index_path = FRONTEND_DIR / "index.html"
    if not index_path.exists():
        raise HTTPException(status_code=404, detail="index.html not found.")
    html = index_path.read_text(encoding="utf-8")
    injection = f'<script>window.__AUTH_TOKEN__ = "{API_TOKEN}";</script>'
    if "</head>" in html:
        html = html.replace("</head>", f"  {injection}\n</head>", 1)
    else:
        html = f"{injection}\n{html}"
    return HTMLResponse(content=html, status_code=200)


# --------------------------------------------------------------------------
# Register Modular Routers
# --------------------------------------------------------------------------
app.include_router(media_router)
app.include_router(downloads_router)
app.include_router(system_router)
app.include_router(cookies_router)
app.include_router(updater_router)

# Mount static frontend assets (css, js) with html=False so index.html hits token injector
if FRONTEND_DIR.exists():
    app.mount("/", StaticFiles(directory=str(FRONTEND_DIR), html=False), name="frontend")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=SERVER_HOST, port=SERVER_PORT, reload=False)
