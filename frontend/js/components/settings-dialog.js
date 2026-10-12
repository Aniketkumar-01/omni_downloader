/**
 * Settings & System Prerequisites Component for OmniDownloader.
 * Manages save location directory configuration, native folder picker dialog,
 * and FFmpeg readiness checks with 1-click winget background installer.
 */

import { 
  getDownloadDir, 
  setDownloadDir, 
  chooseFolder, 
  syncAuthToken, 
  getSystemStatus, 
  installFfmpeg 
} from "../api.js";
import { store } from "../state.js";
import { showToast } from "./toast.js";
import { showError, clearError } from "./error-alert.js";
import { setDownloadButtonState } from "./media-preview.js";

const folderPathInput = document.getElementById("folder-path-input");
const btnBrowseFolder = document.getElementById("btn-browse-folder");

const prerequisitesBanner = document.getElementById("prerequisites-banner");
const btnInstallFfmpeg = document.getElementById("btn-install-ffmpeg");
const btnInstallFfmpegText = document.getElementById("btn-install-ffmpeg-text");
const btnCopyWinget = document.getElementById("btn-copy-winget");
const btnDismissPrereq = document.getElementById("btn-dismiss-prereq");

const cmdPermissionModal = document.getElementById("cmd-permission-modal");
const btnCloseCmdModal = document.getElementById("btn-close-cmd-modal");
const btnModalCopyCmd = document.getElementById("btn-modal-copy-cmd");
const btnDiyCmd = document.getElementById("btn-diy-cmd");
const btnGrantPermissionCmd = document.getElementById("btn-grant-permission-cmd");
const btnGrantPermissionText = document.getElementById("btn-grant-permission-text");
const cmdToRun = document.getElementById("cmd-to-run");

export async function initDownloadDir() {
  if (!folderPathInput) return;
  try {
    await syncAuthToken();
    const res = await getDownloadDir();
    if (res && res.download_dir) {
      folderPathInput.value = res.download_dir;
      folderPathInput.placeholder = res.download_dir;
      store.set("downloadDir", res.download_dir);
    }
  } catch (err) {
    console.warn("Could not retrieve download directory:", err);
  }
}

export async function checkPrerequisites() {
  if (!prerequisitesBanner) return;
  try {
    const status = await getSystemStatus();
    if (status && status.ffmpeg_installed) {
      prerequisitesBanner.style.display = "none";
    } else {
      prerequisitesBanner.style.display = "flex";
    }
  } catch (err) {
    console.debug("Prerequisites check notice:", err);
  }
}

function openCmdModal() {
  if (cmdPermissionModal) {
    cmdPermissionModal.style.display = "flex";
  }
}

function closeCmdModal() {
  if (cmdPermissionModal) {
    cmdPermissionModal.style.display = "none";
  }
}

async function copyWingetCommand() {
  const cmd = (cmdToRun && cmdToRun.textContent) || "winget install Gyan.FFmpeg";
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(cmd);
    } else {
      const ta = document.createElement("textarea");
      ta.value = cmd;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      ta.remove();
    }
    showToast(`Copied to clipboard: '${cmd}'`, "info");
  } catch (_) {
    showToast(`Command: ${cmd}`, "info");
  }
}

export function initSettings() {
  // 1. Browse folder button
  if (btnBrowseFolder) {
    btnBrowseFolder.addEventListener("click", async () => {
      try {
        btnBrowseFolder.disabled = true;
        const originalHtml = btnBrowseFolder.innerHTML;
        btnBrowseFolder.innerHTML = `
          <span class="fetch-spinner" style="width: 12px; height: 12px; border-width: 1.5px; border-color: var(--accent-primary); border-right-color: transparent;"></span>
          <span>Selecting...</span>
        `;
        showToast("Opening folder picker dialog...", "info");

        const res = await chooseFolder();
        btnBrowseFolder.innerHTML = originalHtml;
        btnBrowseFolder.disabled = false;

        if (res && res.status === "success" && (res.path || res.download_dir)) {
          const chosen = res.path || res.download_dir;
          if (folderPathInput) {
            folderPathInput.value = chosen;
            folderPathInput.placeholder = chosen;
          }
          store.set("downloadDir", chosen);
          showToast(`Save location set: ${chosen}`, "info");
          clearError();
          if (store.get("isMediaDownloaded")) {
            setDownloadButtonState("ready");
          }
        } else if (res && res.status === "cancelled") {
          showToast("Folder selection was cancelled.", "info");
        } else if (res && res.status === "error") {
          showError(res.message || "Failed to open folder picker.");
        }
      } catch (err) {
        console.error("Browse folder error:", err);
        showError(err.message || "Failed to launch folder picker.");
        if (btnBrowseFolder) {
          btnBrowseFolder.disabled = false;
          btnBrowseFolder.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
            </svg>
            <span>Browse</span>
          `;
        }
      }
    });
  }

  // 2. Manual Save Location input handling
  if (folderPathInput) {
    const commitPathChange = async () => {
      let raw = folderPathInput.value.trim();
      raw = raw.replace(/^["']+|["']+$/g, '').trim();

      if (!raw) {
        await initDownloadDir();
        return;
      }

      folderPathInput.value = raw;
      try {
        const res = await setDownloadDir(raw);
        if (res && res.download_dir) {
          folderPathInput.value = res.download_dir;
          folderPathInput.placeholder = res.download_dir;
          store.set("downloadDir", res.download_dir);
        }
        showToast("Save location updated", "info");
        clearError();
        if (store.get("isMediaDownloaded")) {
          setDownloadButtonState("ready");
        }
      } catch (err) {
        console.warn("Failed to set download directory:", err);
        showError(err.message || "Invalid save directory path");
      }
    };

    folderPathInput.addEventListener("change", commitPathChange);
    folderPathInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        folderPathInput.blur();
      }
    });
  }

  // 3. Modal controls
  if (btnCloseCmdModal) {
    btnCloseCmdModal.addEventListener("click", closeCmdModal);
  }
  if (cmdPermissionModal) {
    cmdPermissionModal.addEventListener("click", (e) => {
      if (e.target === cmdPermissionModal) closeCmdModal();
    });
  }
  if (btnModalCopyCmd) {
    btnModalCopyCmd.addEventListener("click", copyWingetCommand);
  }
  if (btnDiyCmd) {
    btnDiyCmd.addEventListener("click", async () => {
      await copyWingetCommand();
      showToast("Command copied! Open your PowerShell or CMD terminal and run it whenever you want.", "success");
      closeCmdModal();
    });
  }
  if (btnInstallFfmpeg) {
    btnInstallFfmpeg.addEventListener("click", openCmdModal);
  }

  // 4. Grant permission & run winget installation
  if (btnGrantPermissionCmd) {
    btnGrantPermissionCmd.addEventListener("click", async () => {
      try {
        btnGrantPermissionCmd.disabled = true;
        if (btnGrantPermissionText) {
          btnGrantPermissionText.textContent = "Installing on your PC (silent background)...";
        }
        showToast("Installing FFmpeg locally on your PC (no windows will pop up)...", "info");

        const res = await installFfmpeg();
        showToast(res.message || "FFmpeg installed successfully!", "success");

        if (prerequisitesBanner) {
          prerequisitesBanner.classList.add("prereq-ready");
          const titleEl = prerequisitesBanner.querySelector(".prereq-title");
          const descEl = document.getElementById("prereq-desc");
          if (titleEl) titleEl.textContent = "✓ FFmpeg Ready";
          if (descEl) descEl.textContent = "High-resolution stream merging (1080p, 4K) and MP3 conversion are now active.";
          if (btnInstallFfmpeg) btnInstallFfmpeg.style.display = "none";
        }

        closeCmdModal();

        setTimeout(() => {
          if (prerequisitesBanner) {
            prerequisitesBanner.style.transition = "all 0.5s ease";
            prerequisitesBanner.style.opacity = "0";
            setTimeout(() => {
              prerequisitesBanner.style.display = "none";
            }, 500);
          }
        }, 4000);
      } catch (err) {
        console.error("FFmpeg install error:", err);
        showError(err.message || "Could not install FFmpeg automatically. You can copy the command and run it in terminal.");
        btnGrantPermissionCmd.disabled = false;
        if (btnGrantPermissionText) {
          btnGrantPermissionText.textContent = "Retry Installation";
        }
      }
    });
  }

  if (btnCopyWinget) {
    btnCopyWinget.addEventListener("click", copyWingetCommand);
  }

  if (btnDismissPrereq) {
    btnDismissPrereq.addEventListener("click", () => {
      if (prerequisitesBanner) prerequisitesBanner.style.display = "none";
    });
  }

  // Run initial queries
  initDownloadDir();
  checkPrerequisites();
}
