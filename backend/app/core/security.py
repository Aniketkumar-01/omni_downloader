"""
Security constants, authentication tokens, and Windows process execution flags.
"""

import os
import secrets
import subprocess

# Startup random token for local API protection against unauthorized local processes
API_TOKEN: str = secrets.token_urlsafe(32)

# Windows process creation flag to prevent console/cmd windows from popping up
NO_WINDOW_FLAG: int = getattr(subprocess, 'CREATE_NO_WINDOW', 0x08000000) if os.name == 'nt' else 0
