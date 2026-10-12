/**
 * Application & Engine Updater Component for OmniDownloader.
 * Manages GitHub Releases update checking, streaming installer downloads,
 * yt-dlp backend engine updates, and process heartbeat monitoring.
 */

import { 
  getAppVersion, 
  getEngineVersion, 
  updateEngine, 
  checkAppUpdates, 
  startAppUpdateDownload, 
  getAppUpdateProgress, 
  cancelAppUpdate, 
  applyAppUpdate,
  syncAuthToken 
} from "../api.js";
import { showToast } from "./toast.js";
import { clearError } from "./error-alert.js";
import { formatBytes } from "./media-preview.js";

// DOM Elements
const btnCheckUpdates = document.getElementById("btn-check-updates");
const updateBtnLabel = document.getElementById("update-btn-label");
const updateBadgeDot = document.getElementById("update-badge-dot");

const appUpdateModal = document.getElementById("app-update-modal");
const btnCloseUpdateModal = document.getElementById("btn-close-update-modal");
const btnUpdateLater = document.getElementById("btn-update-later");
const btnUpdateAction = document.getElementById("btn-update-action");
const btnUpdateActionText = document.getElementById("btn-update-action-text");
const btnUpdateGithubLink = document.getElementById("btn-update-github-link");
const btnUpdateCancel = document.getElementById("btn-update-cancel");

const updateCurrentVersion = document.getElementById("update-current-version");
const updateTargetVersion = document.getElementById("update-target-version");
const updateDateText = document.getElementById("update-date-text");
const updateSizeText = document.getElementById("update-size-text");
const updateNotesContent = document.getElementById("update-notes-content");

const updateDownloadDeck = document.getElementById("update-download-deck");
const updateDownloadStatusLabel = document.getElementById("update-download-status-label");
const updateDownloadPercentLabel = document.getElementById("update-download-percent-label");
const updateDownloadBarFill = document.getElementById("update-download-bar-fill");
const updateDownloadBytesText = document.getElementById("update-download-bytes-text");
const updateDownloadSpeedText = document.getElementById("update-download-speed-text");

const appVersionIndicator = document.getElementById("app-version-indicator");
const engineVersionIndicator = document.getElementById("engine-version-indicator");
const engineBadgeText = document.getElementById("engine-badge-text");
const btnUpdateEngine = document.getElementById("btn-update-engine");
const btnUpdateEngineText = document.getElementById("btn-update-engine-text");
const engineUpdateSpinner = document.getElementById("engine-update-spinner");

let latestUpdateData = null;
let updatePollTimer = null;
let isUpdateDownloading = false;
let isUpdateReadyToInstall = false;
let onRetrySubmissionCallback = null;

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str || "";
  return div.innerHTML;
}

function formatReleaseNotes(markdown) {
  if (!markdown || !markdown.trim()) {
    return "<p>No release notes provided for this version.</p>";
  }
  
  const lines = markdown.split("\n");
  let html = "";
  let inList = false;

  for (let line of lines) {
    let trimmed = line.trim();
    if (!trimmed) {
      if (inList) {
        html += "</ul>";
        inList = false;
      }
      continue;
    }

    if (trimmed.startsWith("#")) {
      if (inList) {
        html += "</ul>";
        inList = false;
      }
      const headingText = trimmed.replace(/^#+\s*/, "");
      html += `<div style="font-weight: 700; color: #fff; margin: 0.5rem 0 0.25rem;">${escapeHtml(headingText)}</div>`;
      continue;
    }

    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
      if (!inList) {
        html += "<ul>";
        inList = true;
      }
      let itemText = trimmed.substring(2);
      itemText = escapeHtml(itemText)
        .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
        .replace(/`([^`]+)`/g, "<code>$1</code>");
      html += `<li>${itemText}</li>`;
      continue;
    }

    if (inList) {
      html += "</ul>";
      inList = false;
    }
    let pText = escapeHtml(trimmed)
      .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
      .replace(/`([^`]+)`/g, "<code>$1</code>");
    html += `<p style="margin: 0.25rem 0;">${pText}</p>`;
  }

  if (inList) html += "</ul>";
  return html;
}

export function openUpdateModal(data) {
  if (!appUpdateModal) return;

  if (updateCurrentVersion) {
    updateCurrentVersion.textContent = `v${data.current_version || "1.2.4"}`;
  }
  if (updateTargetVersion) {
    updateTargetVersion.textContent = data.tag_name || `v${data.latest_version}`;
  }
  if (updateDateText) {
    if (data.published_at) {
      try {
        const d = new Date(data.published_at);
        updateDateText.textContent = `Released: ${d.toLocaleDateString()}`;
      } catch (_) {
        updateDateText.textContent = "Released: Recent";
      }
    } else {
      updateDateText.textContent = "Released: Recent";
    }
  }
  if (updateSizeText) {
    updateSizeText.textContent = data.file_size ? `Size: ${formatBytes(data.file_size)}` : "Size: ~48 MB";
  }
  if (updateNotesContent) {
    updateNotesContent.innerHTML = formatReleaseNotes(data.release_notes);
  }
  if (btnUpdateGithubLink) {
    btnUpdateGithubLink.href = data.release_url || "https://github.com/Aniketkumar-01/omni_downloader/releases";
  }

  if (!isUpdateDownloading && !isUpdateReadyToInstall) {
    if (updateDownloadDeck) updateDownloadDeck.style.display = "none";
    if (btnUpdateCancel) btnUpdateCancel.style.display = "none";
    if (btnUpdateLater) btnUpdateLater.style.display = "inline-flex";
    if (btnUpdateAction) {
      btnUpdateAction.disabled = false;
      if (btnUpdateActionText) btnUpdateActionText.textContent = "Download & Install";
    }
  }

  appUpdateModal.style.display = "flex";
}

export function closeUpdateModal() {
  if (!appUpdateModal) return;
  appUpdateModal.style.display = "none";
}

export async function handleCheckForUpdates(manualClick = true) {
  if (!btnCheckUpdates) return;

  btnCheckUpdates.classList.add("is-checking");
  if (updateBtnLabel) updateBtnLabel.textContent = "Checking...";

  try {
    const data = await checkAppUpdates(manualClick);
    if (data.status === "success" && data.update_available) {
      latestUpdateData = data;
      btnCheckUpdates.classList.add("has-update");
      if (updateBadgeDot) updateBadgeDot.style.display = "block";

      if (manualClick) {
        openUpdateModal(data);
      }
    } else if (data.status === "success" && !data.update_available) {
      btnCheckUpdates.classList.remove("has-update");
      if (updateBadgeDot) updateBadgeDot.style.display = "none";

      if (manualClick) {
        showToast(`You're up to date! OmniDownloader v${data.current_version || "1.2.4"} is the latest version.`, "success");
      }
    } else {
      if (manualClick) {
        showToast(data.message || "Could not check for updates. Please try again later.", "error");
      }
    }
  } catch (err) {
    console.error("Update check failed:", err);
    if (manualClick) {
      showToast("Unable to reach update server. Check your internet connection.", "error");
    }
  } finally {
    btnCheckUpdates.classList.remove("is-checking");
    if (updateBtnLabel) updateBtnLabel.textContent = "Check for Updates";
  }
}

async function handleStartUpdate() {
  if (isUpdateReadyToInstall) {
    try {
      if (btnUpdateAction) btnUpdateAction.disabled = true;
      if (btnUpdateActionText) btnUpdateActionText.textContent = "Restarting...";
      showToast("Launching installer and restarting OmniDownloader...", "info", 5000);
      await applyAppUpdate(latestUpdateData?.downloaded_path, true);
    } catch (err) {
      showToast(err.message || "Failed to launch installer.", "error");
      if (btnUpdateAction) btnUpdateAction.disabled = false;
      if (btnUpdateActionText) btnUpdateActionText.textContent = "Retry Install";
    }
    return;
  }

  if (!latestUpdateData || !latestUpdateData.download_url) {
    showToast("Update installer not directly downloadable. Opening release page...", "info");
    if (latestUpdateData?.release_url) {
      window.open(latestUpdateData.release_url, "_blank");
    }
    return;
  }

  isUpdateDownloading = true;
  if (updateDownloadDeck) updateDownloadDeck.style.display = "flex";
  if (btnUpdateCancel) btnUpdateCancel.style.display = "inline-flex";
  if (btnUpdateLater) btnUpdateLater.style.display = "none";
  if (btnUpdateAction) btnUpdateAction.disabled = true;
  if (btnUpdateActionText) btnUpdateActionText.textContent = "Downloading...";

  try {
    await startAppUpdateDownload(
      latestUpdateData.download_url,
      latestUpdateData.file_size || 0,
      latestUpdateData.tag_name || "latest"
    );

    if (updatePollTimer) clearInterval(updatePollTimer);
    updatePollTimer = setInterval(async () => {
      try {
        const progress = await getAppUpdateProgress();
        if (progress.status === "downloading") {
          const pct = Math.min(100, Math.max(0, progress.percent || 0));
          if (updateDownloadPercentLabel) updateDownloadPercentLabel.textContent = `${pct.toFixed(0)}%`;
          if (updateDownloadBarFill) updateDownloadBarFill.style.width = `${pct}%`;
          if (updateDownloadStatusLabel) updateDownloadStatusLabel.textContent = "Downloading installer...";
          if (updateDownloadBytesText) {
            updateDownloadBytesText.textContent = `${formatBytes(progress.downloaded_bytes)} / ${formatBytes(progress.total_bytes)}`;
          }
          if (updateDownloadSpeedText) {
            updateDownloadSpeedText.textContent = progress.speed_str || "--";
          }
        } else if (progress.status === "completed") {
          clearInterval(updatePollTimer);
          updatePollTimer = null;
          isUpdateDownloading = false;
          isUpdateReadyToInstall = true;

          if (latestUpdateData) {
            latestUpdateData.downloaded_path = progress.file_path;
          }

          if (updateDownloadPercentLabel) updateDownloadPercentLabel.textContent = "100%";
          if (updateDownloadBarFill) updateDownloadBarFill.style.width = "100%";
          if (updateDownloadStatusLabel) {
            updateDownloadStatusLabel.textContent = "✓ Verified & Ready to Install";
          }
          if (updateDownloadSpeedText) updateDownloadSpeedText.textContent = "Completed";

          if (btnUpdateCancel) btnUpdateCancel.style.display = "none";
          if (btnUpdateAction) {
            btnUpdateAction.disabled = false;
            if (btnUpdateActionText) btnUpdateActionText.textContent = "Install & Restart Now";
          }
          showToast("Update downloaded! Click 'Install & Restart Now' to finish.", "success", 4000);
        } else if (progress.status === "failed") {
          clearInterval(updatePollTimer);
          updatePollTimer = null;
          isUpdateDownloading = false;
          if (updateDownloadStatusLabel) {
            updateDownloadStatusLabel.textContent = `Download Failed: ${progress.error || "Network error"}`;
          }
          if (btnUpdateAction) {
            btnUpdateAction.disabled = false;
            if (btnUpdateActionText) btnUpdateActionText.textContent = "Retry Download";
          }
          if (btnUpdateCancel) btnUpdateCancel.style.display = "none";
          if (btnUpdateLater) btnUpdateLater.style.display = "inline-flex";
          showToast(`Download failed: ${progress.error || "Network error"}`, "error");
        } else if (progress.status === "cancelled") {
          clearInterval(updatePollTimer);
          updatePollTimer = null;
          isUpdateDownloading = false;
          if (updateDownloadDeck) updateDownloadDeck.style.display = "none";
          if (btnUpdateCancel) btnUpdateCancel.style.display = "none";
          if (btnUpdateLater) btnUpdateLater.style.display = "inline-flex";
          if (btnUpdateAction) {
            btnUpdateAction.disabled = false;
            if (btnUpdateActionText) btnUpdateActionText.textContent = "Download & Install";
          }
          showToast("Update download cancelled.", "info");
        }
      } catch (pollErr) {
        console.error("Progress poll error:", pollErr);
      }
    }, 500);

  } catch (err) {
    isUpdateDownloading = false;
    showToast(err.message || "Could not start download.", "error");
    if (btnUpdateAction) {
      btnUpdateAction.disabled = false;
      if (btnUpdateActionText) btnUpdateActionText.textContent = "Download & Install";
    }
    if (btnUpdateCancel) btnUpdateCancel.style.display = "none";
    if (btnUpdateLater) btnUpdateLater.style.display = "inline-flex";
  }
}

async function handleCancelUpdate() {
  try {
    await cancelAppUpdate();
  } catch (err) {
    console.warn("Cancel update error:", err);
  }
}

async function sendHeartbeat() {
  try {
    const token = await syncAuthToken();
    const url = token ? `/api/heartbeat?token=${encodeURIComponent(token)}` : "/api/heartbeat";
    const res = await fetch(url, { method: "POST" });
    if (res.ok) {
      const serverStatusIndicator = document.getElementById("server-status-indicator");
      if (serverStatusIndicator && !serverStatusIndicator.classList.contains("active")) {
        serverStatusIndicator.classList.add("active");
        const label = serverStatusIndicator.querySelector(".node-label");
        if (label) label.textContent = "Server Ready";
      }
    }
  } catch (_) {
    const serverStatusIndicator = document.getElementById("server-status-indicator");
    if (serverStatusIndicator && serverStatusIndicator.classList.contains("active")) {
      serverStatusIndicator.classList.remove("active");
      const label = serverStatusIndicator.querySelector(".node-label");
      if (label) label.textContent = "Reconnecting...";
    }
  }
}

export function initUpdater(options = {}) {
  if (options.onRetrySubmission) {
    onRetrySubmissionCallback = options.onRetrySubmission;
  }

  // App updates listeners
  if (btnCheckUpdates) {
    btnCheckUpdates.addEventListener("click", () => handleCheckForUpdates(true));
  }
  if (btnCloseUpdateModal) {
    btnCloseUpdateModal.addEventListener("click", closeUpdateModal);
  }
  if (btnUpdateLater) {
    btnUpdateLater.addEventListener("click", closeUpdateModal);
  }
  if (appUpdateModal) {
    appUpdateModal.addEventListener("click", (e) => {
      if (e.target === appUpdateModal && !isUpdateDownloading) {
        closeUpdateModal();
      }
    });
  }
  if (btnUpdateAction) {
    btnUpdateAction.addEventListener("click", handleStartUpdate);
  }
  if (btnUpdateCancel) {
    btnUpdateCancel.addEventListener("click", handleCancelUpdate);
  }

  // App version indicator
  if (appVersionIndicator) {
    getAppVersion().then(data => {
      if (data && data.version) {
        appVersionIndicator.textContent = `v${data.version}`;
      }
    }).catch(() => {});

    appVersionIndicator.style.cursor = "pointer";
    appVersionIndicator.addEventListener("click", () => {
      handleCheckForUpdates(true);
    });
  }

  // Engine version display & 1-click update
  if (engineBadgeText) {
    getEngineVersion().then(data => {
      if (data && data.installed_version) {
        engineBadgeText.textContent = `Engine v${data.installed_version}`;
      }
    }).catch(() => {});
  }

  if (engineVersionIndicator) {
    engineVersionIndicator.style.cursor = "pointer";
    engineVersionIndicator.addEventListener("click", () => {
      if (btnUpdateEngine) btnUpdateEngine.click();
    });
  }

  if (btnUpdateEngine) {
    btnUpdateEngine.addEventListener("click", async () => {
      btnUpdateEngine.disabled = true;
      if (btnUpdateEngineText) btnUpdateEngineText.textContent = "Updating Engine...";
      if (engineUpdateSpinner) engineUpdateSpinner.style.display = "inline-block";
      try {
        const res = await updateEngine();
        const newVer = (res && res.current_version) || "latest";
        if (engineBadgeText) engineBadgeText.textContent = `Engine v${newVer}`;
        showToast(`Download engine successfully updated to v${newVer}! Retrying fetch...`, "success", 3500);
        clearError();
        if (onRetrySubmissionCallback) {
          onRetrySubmissionCallback();
        }
      } catch (err) {
        console.error("Engine update failed:", err);
        showToast(err.message || "Engine update failed. Please check your internet connection.", "error");
      } finally {
        btnUpdateEngine.disabled = false;
        if (btnUpdateEngineText) btnUpdateEngineText.textContent = "Update Download Engine Now";
        if (engineUpdateSpinner) engineUpdateSpinner.style.display = "none";
      }
    });
  }

  // Heartbeat & lifecycle
  sendHeartbeat();
  setInterval(sendHeartbeat, 3000);
  window.addEventListener("focus", sendHeartbeat);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      sendHeartbeat();
    }
  });

  window.addEventListener("pagehide", () => {
    const token = window.__AUTH_TOKEN__ || "";
    const shutdownUrl = `/api/shutdown?token=${encodeURIComponent(token)}`;
    if (navigator.sendBeacon) {
      navigator.sendBeacon(shutdownUrl);
    } else {
      fetch(shutdownUrl, { method: "POST", keepalive: true }).catch(() => {});
    }
  });

  // Non-blocking silent background update check
  setTimeout(() => {
    handleCheckForUpdates(false);
  }, 3500);
}
