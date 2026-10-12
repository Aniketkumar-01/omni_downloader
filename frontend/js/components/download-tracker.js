/**
 * Download Tracker Component for OmniDownloader.
 * Manages live progress gauges, SSE streaming, pause/resume, cancellation,
 * and post-download actions (Open in Explorer / Play media file).
 */

import { 
  cancelTask, 
  pauseTask, 
  resumeTask, 
  openDownloadsFolder, 
  openFile 
} from "../api.js";
import { ProgressTracker } from "../progress.js";
import { store } from "../state.js";
import { showToast } from "./toast.js";
import { showError } from "./error-alert.js";
import { formatBytes, setDownloadButtonState } from "./media-preview.js";

const progressCard = document.getElementById("progress-card");
const progressItemTitle = document.getElementById("progress-item-title");
const progressPercentage = document.getElementById("progress-percentage");
const progressBarFill = document.getElementById("progress-bar-fill");
const progressSpeed = document.getElementById("progress-speed");
const progressEta = document.getElementById("progress-eta");
const progressSize = document.getElementById("progress-size");
const btnCancelDownload = document.getElementById("btn-cancel-download");
const btnPauseDownload = document.getElementById("btn-pause-download");
const btnPauseText = document.getElementById("btn-pause-text");
const iconPause = document.getElementById("icon-pause");
const iconResume = document.getElementById("icon-resume");
const progressActions = document.getElementById("progress-actions");
const btnOpenFolder = document.getElementById("btn-open-folder");
const btnPlayFile = document.getElementById("btn-play-file");
const btnDownloadAgain = document.getElementById("btn-download-again");
const qualitySelect = document.getElementById("quality-select");

let activeTracker = null;
let isPaused = false;
let restartCallback = null;

export function hideProgressView() {
  if (progressCard) {
    progressCard.style.display = "none";
  }
}

export function showProgressView(taskId) {
  if (!progressCard) return;

  store.set("currentTaskId", taskId);
  store.set("isDownloading", true);

  progressCard.style.display = "block";
  if (progressActions) progressActions.style.display = "none";
  if (progressPercentage) progressPercentage.textContent = "0%";
  if (progressBarFill) {
    progressBarFill.style.width = "0%";
    progressBarFill.style.background = "linear-gradient(90deg, #00d2ff, #00f0b5)";
  }
  if (progressSpeed) progressSpeed.textContent = "Speed: Calculating...";
  if (progressEta) progressEta.textContent = "ETA: --";
  if (progressItemTitle) progressItemTitle.textContent = "Initializing download...";

  const currentMedia = store.get("currentMedia");
  const selectedQuality = qualitySelect ? qualitySelect.value : "best";
  const lockedTotalFormatted = (currentMedia && currentMedia.quality_sizes_formatted && currentMedia.quality_sizes_formatted[selectedQuality])
    || (currentMedia && currentMedia.filesize_formatted)
    || null;

  if (progressSize) {
    progressSize.textContent = lockedTotalFormatted ? `Size: ${lockedTotalFormatted}` : "Size: Calculating...";
  }

  isPaused = false;
  if (btnPauseDownload) {
    btnPauseDownload.style.display = "inline-flex";
    btnPauseDownload.classList.remove("is-paused");
    if (btnPauseText) btnPauseText.textContent = "Pause";
    if (iconPause) iconPause.style.display = "inline";
    if (iconResume) iconResume.style.display = "none";
  }

  if (btnCancelDownload) {
    btnCancelDownload.style.display = "inline-flex";
    btnCancelDownload.disabled = false;
    btnCancelDownload.innerHTML = `
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <line x1="18" y1="6" x2="6" y2="18"></line>
        <line x1="6" y1="6" x2="18" y2="18"></line>
      </svg>
      <span>Cancel</span>
    `;
  }

  progressCard.scrollIntoView({ behavior: "smooth", block: "nearest" });

  if (activeTracker) {
    activeTracker.close();
  }

  activeTracker = new ProgressTracker(taskId, {
    onProgress: (data) => {
      const pct = Math.min(100, Math.max(0, data.progress || 0));
      if (progressBarFill) progressBarFill.style.width = `${pct}%`;
      if (progressPercentage) progressPercentage.textContent = `${pct.toFixed(1)}%`;
      if (progressSpeed) progressSpeed.textContent = `Speed: ${data.speed_str || "--"}`;
      if (progressEta) progressEta.textContent = `ETA: ${data.eta_str || "--"}`;

      if (data.status === "paused") {
        isPaused = true;
        if (progressBarFill) progressBarFill.style.background = "linear-gradient(90deg, #f59e0b, #fbbf24)";
        if (progressSpeed) progressSpeed.textContent = "Speed: Paused";
        if (btnPauseDownload) {
          btnPauseDownload.classList.add("is-paused");
          if (btnPauseText) btnPauseText.textContent = "Resume";
          if (iconPause) iconPause.style.display = "none";
          if (iconResume) iconResume.style.display = "inline";
        }
      } else if (data.status === "downloading" && isPaused) {
        isPaused = false;
        if (progressBarFill) progressBarFill.style.background = "linear-gradient(90deg, #00d2ff, #00f0b5)";
        if (btnPauseDownload) {
          btnPauseDownload.classList.remove("is-paused");
          if (btnPauseText) btnPauseText.textContent = "Pause";
          if (iconPause) iconPause.style.display = "inline";
          if (iconResume) iconResume.style.display = "none";
        }
      }

      if (progressSize) {
        const dlStr = (data.downloaded_bytes && data.downloaded_bytes > 0) ? formatBytes(data.downloaded_bytes) : null;
        const totStr = lockedTotalFormatted || ((data.total_bytes && data.total_bytes > 0) ? formatBytes(data.total_bytes) : null);
        if (dlStr && totStr) {
          progressSize.textContent = `Size: ${dlStr} / ${totStr}`;
        } else if (dlStr) {
          progressSize.textContent = `Size: ${dlStr}`;
        } else if (totStr) {
          progressSize.textContent = `Size: ${totStr}`;
        }
      }

      if (data.current_item && progressItemTitle) {
        progressItemTitle.textContent = data.current_item;
      }

      if (data.status === "completed" || pct >= 100) {
        if (btnCancelDownload) btnCancelDownload.style.display = "none";
        if (btnPauseDownload) btnPauseDownload.style.display = "none";
      }

      setDownloadButtonState("downloading", `Downloading (${pct.toFixed(0)}%)...`);
    },
    onComplete: (data) => {
      store.set("isDownloading", false);
      store.set("isMediaDownloaded", true);

      if (btnCancelDownload) btnCancelDownload.style.display = "none";
      if (btnPauseDownload) btnPauseDownload.style.display = "none";
      if (progressBarFill) progressBarFill.style.width = "100%";
      if (progressPercentage) progressPercentage.textContent = "100%";
      if (progressSpeed) progressSpeed.textContent = "Speed: Complete";
      if (progressEta) progressEta.textContent = "ETA: Done";
      if (progressItemTitle) progressItemTitle.textContent = "Download completed successfully!";

      if (progressSize) {
        const finalSize = (data.total_bytes && data.total_bytes > 0) ? formatBytes(data.total_bytes) : lockedTotalFormatted;
        if (finalSize) {
          progressSize.textContent = `Total: ${finalSize}`;
        }
      }

      if (progressActions) progressActions.style.display = "flex";
      setDownloadButtonState("completed");
    },
    onCancel: () => {
      store.set("isDownloading", false);
      if (btnCancelDownload) btnCancelDownload.style.display = "none";
      if (btnPauseDownload) btnPauseDownload.style.display = "none";
      if (progressBarFill) {
        progressBarFill.style.width = "100%";
        progressBarFill.style.background = "#f59e0b";
      }
      if (progressPercentage) progressPercentage.textContent = "Cancelled";
      if (progressSpeed) progressSpeed.textContent = "Speed: --";
      if (progressEta) progressEta.textContent = "ETA: --";
      if (progressItemTitle) progressItemTitle.textContent = "Download was cancelled.";
      setDownloadButtonState("ready");
      showToast("Download was cancelled.", "info");
    },
    onError: (err) => {
      store.set("isDownloading", false);
      if (btnCancelDownload) btnCancelDownload.style.display = "none";
      if (btnPauseDownload) btnPauseDownload.style.display = "none";
      showError(err);
      if (progressItemTitle) progressItemTitle.textContent = "Download failed.";
      if (progressBarFill) progressBarFill.style.background = "#ef4444";
      if (progressPercentage) progressPercentage.textContent = "Failed";
      setDownloadButtonState("ready");
    },
  });

  activeTracker.start();
}

export function cancelActiveDownload() {
  const currentTaskId = store.get("currentTaskId");
  if (!currentTaskId) return;

  if (activeTracker) {
    activeTracker.close();
  }

  if (btnCancelDownload) btnCancelDownload.style.display = "none";
  if (btnPauseDownload) btnPauseDownload.style.display = "none";
  if (progressBarFill) {
    progressBarFill.style.width = "100%";
    progressBarFill.style.background = "#f59e0b";
  }
  if (progressPercentage) progressPercentage.textContent = "Cancelled";
  if (progressSpeed) progressSpeed.textContent = "Speed: --";
  if (progressEta) progressEta.textContent = "ETA: --";
  if (progressItemTitle) progressItemTitle.textContent = "Download was cancelled.";
  setDownloadButtonState("ready");
  showToast("Download cancelled.", "info");

  cancelTask(currentTaskId).catch(err => {
    console.warn("Cancel request notice:", err);
  });
}

export function initDownloadTracker(options = {}) {
  if (options.onRestartDownload) {
    restartCallback = options.onRestartDownload;
  }

  if (btnPauseDownload) {
    btnPauseDownload.addEventListener("click", async () => {
      const currentTaskId = store.get("currentTaskId");
      if (!currentTaskId) return;
      try {
        if (!isPaused) {
          await pauseTask(currentTaskId);
          isPaused = true;
          if (progressBarFill) progressBarFill.style.background = "linear-gradient(90deg, #f59e0b, #fbbf24)";
          if (progressSpeed) progressSpeed.textContent = "Speed: Paused";
          btnPauseDownload.classList.add("is-paused");
          if (btnPauseText) btnPauseText.textContent = "Resume";
          if (iconPause) iconPause.style.display = "none";
          if (iconResume) iconResume.style.display = "inline";
          showToast("Download paused.", "info", 1500);
        } else {
          await resumeTask(currentTaskId);
          isPaused = false;
          if (progressBarFill) progressBarFill.style.background = "linear-gradient(90deg, #00d2ff, #00f0b5)";
          if (progressSpeed) progressSpeed.textContent = "Speed: Resuming...";
          btnPauseDownload.classList.remove("is-paused");
          if (btnPauseText) btnPauseText.textContent = "Pause";
          if (iconPause) iconPause.style.display = "inline";
          if (iconResume) iconResume.style.display = "none";
          showToast("Download resumed.", "info", 1500);
        }
      } catch (err) {
        console.warn("Pause/resume error:", err);
        showToast(err.message || "Failed to toggle pause.", "error");
      }
    });
  }

  if (btnCancelDownload) {
    btnCancelDownload.addEventListener("click", cancelActiveDownload);
  }

  if (btnOpenFolder) {
    btnOpenFolder.addEventListener("click", async () => {
      const currentTaskId = store.get("currentTaskId");
      try {
        showToast("Opening folder...", "info");
        await openDownloadsFolder(currentTaskId);
      } catch (err) {
        console.error("Failed to open folder:", err);
        showError("Could not reveal file in folder.");
      }
    });
  }

  if (btnPlayFile) {
    btnPlayFile.addEventListener("click", async () => {
      const currentTaskId = store.get("currentTaskId");
      if (!currentTaskId) return;
      try {
        btnPlayFile.disabled = true;
        showToast("Launching media player...", "info");
        await openFile(currentTaskId);
      } catch (err) {
        console.error("Failed to open file:", err);
        showError(err.message || "Could not launch media player.");
      } finally {
        setTimeout(() => {
          if (btnPlayFile) btnPlayFile.disabled = false;
        }, 600);
      }
    });
  }

  if (btnDownloadAgain) {
    btnDownloadAgain.addEventListener("click", () => {
      const currentMedia = store.get("currentMedia");
      if (!currentMedia) return;
      store.set("isMediaDownloaded", false);
      setDownloadButtonState("ready");
      showToast("Restarting download...", "info");
      if (restartCallback) {
        restartCallback();
      }
    });
  }
}
