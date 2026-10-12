/**
 * OmniDownloader Frontend Main Orchestrator.
 * Wires together modular UI components, reactive state management,
 * URL input pipelines, and keyboard shortcuts.
 */

import { store } from "./state.js";
import { 
  fetchMediaInfo, 
  fetchBatchMediaInfo, 
  startDownload, 
  getClipboardText, 
  openDownloadsFolder, 
  openFile 
} from "./api.js";
import { showToast } from "./components/toast.js";
import { showError, clearError, initErrorAlert } from "./components/error-alert.js";
import { 
  renderMediaInfo, 
  hideMediaPreview, 
  initMediaPreview, 
  setDownloadButtonState 
} from "./components/media-preview.js";
import { 
  renderPlaylist, 
  hidePlaylist, 
  initPlaylistManager, 
  getSelectedVideoIds 
} from "./components/playlist-manager.js";
import { 
  showProgressView, 
  hideProgressView, 
  initDownloadTracker 
} from "./components/download-tracker.js";
import { initSettings } from "./components/settings-dialog.js";
import { initUpdater } from "./components/updater-dialog.js";

// DOM Elements
const urlForm = document.getElementById("url-form");
const urlInput = document.getElementById("url-input");
const batchInput = document.getElementById("batch-input");
const btnBatchToggle = document.getElementById("btn-batch-toggle");
const batchToggleLabel = document.getElementById("batch-toggle-label");
const btnClear = document.getElementById("btn-clear");
const btnPaste = document.getElementById("btn-paste");
const btnSubmit = document.getElementById("btn-submit");
const btnSubmitText = document.getElementById("btn-submit-text");
const btnSpinner = document.getElementById("btn-spinner");
const platformPills = document.querySelectorAll(".platform-pill");
const cookieSelect = document.getElementById("cookie-select");
const qualitySelect = document.getElementById("quality-select");
const folderPathInput = document.getElementById("folder-path-input");
const btnDownload = document.getElementById("btn-download");

let isBatchMode = false;

// URL Extraction Helper
function extractUrlsFromText(rawText) {
  if (!rawText) return [];
  const lines = rawText.split(/[\r\n,]+/).map(s => s.trim()).filter(Boolean);
  const urls = [];
  for (const line of lines) {
    const httpMatches = line.match(/https?:\/\/[^\s]+/gi);
    if (httpMatches) {
      urls.push(...httpMatches);
    } else {
      const token = line.trim();
      if (token.includes(".") && !token.includes(" ")) {
        urls.push(`https://${token}`);
      }
    }
  }
  return [...new Set(urls)];
}

// Batch Mode Toggle
function setBatchMode(active) {
  isBatchMode = active;
  store.set("isBatchMode", active);

  if (isBatchMode) {
    if (btnBatchToggle) btnBatchToggle.classList.add("active");
    if (batchToggleLabel) batchToggleLabel.textContent = "Single";
    if (urlInput) urlInput.style.display = "none";
    if (batchInput) {
      batchInput.style.display = "block";
      if (urlInput && urlInput.value) batchInput.value = urlInput.value;
      const firstUrl = extractUrlsFromText(batchInput.value)[0] || "";
      updatePlatformHighlights(firstUrl);
      batchInput.focus();
    }
    if (btnClear) btnClear.style.display = (batchInput && batchInput.value) ? "flex" : "none";
  } else {
    if (btnBatchToggle) btnBatchToggle.classList.remove("active");
    if (batchToggleLabel) batchToggleLabel.textContent = "Batch";
    if (batchInput) batchInput.style.display = "none";
    if (urlInput) {
      urlInput.style.display = "block";
      if (batchInput && batchInput.value) {
        urlInput.value = batchInput.value.split(/[\r\n]+/)[0].trim() || "";
        updatePlatformHighlights(urlInput.value);
      }
      urlInput.focus();
    }
    if (btnClear) btnClear.style.display = (urlInput && urlInput.value) ? "flex" : "none";
  }
}

// Platform detection highlight
function updatePlatformHighlights(url) {
  const u = (url || "").toLowerCase();
  let detected = null;
  if (u.includes("youtube.com") || u.includes("youtu.be")) detected = "youtube";
  else if (u.includes("instagram.com")) detected = "instagram";
  else if (u.includes("tiktok.com")) detected = "tiktok";
  else if (u.includes("twitter.com") || u.includes("x.com")) detected = "twitter";
  else if (u.includes("bilibili.com") || u.includes("b23.tv")) detected = "bilibili";
  else if (u.includes("facebook.com") || u.includes("fb.watch")) detected = "facebook";
  else if (u.includes("reddit.com")) detected = "reddit";

  platformPills.forEach((pill) => {
    if (detected && pill.dataset.platform === detected) {
      pill.classList.add("active");
    } else {
      pill.classList.remove("active");
    }
  });
  return detected;
}

// Loading state indicator for submit button
function setLoading(isLoading) {
  if (!btnSubmit) return;
  btnSubmit.disabled = isLoading;
  if (btnSubmitText) btnSubmitText.textContent = isLoading ? "Fetching..." : "Fetch Info";
  if (btnSpinner) btnSpinner.style.display = isLoading ? "inline-block" : "none";
}

// Unified URL & Batch Submission Handler
async function handleUrlSubmission(e) {
  if (e) {
    e.preventDefault();
    e.stopPropagation();
  }
  clearError();

  const currentText = ((isBatchMode && batchInput) ? batchInput.value : (urlInput ? urlInput.value : "")) || "";
  const extracted = extractUrlsFromText(currentText);

  if (extracted.length === 0) {
    showError("Please enter at least one valid video or playlist URL.");
    if (isBatchMode && batchInput) batchInput.focus();
    else if (urlInput) urlInput.focus();
    return;
  }

  if (!isBatchMode && extracted.length > 1) {
    setBatchMode(true);
    if (batchInput) batchInput.value = extracted.join("\n");
    showToast(`Detected ${extracted.length} URLs - switched to Batch Mode`, "info");
  }

  setLoading(true);
  hideMediaPreview();
  hideProgressView();

  try {
    const cookieBrowser = cookieSelect ? cookieSelect.value : null;
    let info;
    if (extracted.length > 1) {
      info = await fetchBatchMediaInfo(extracted, cookieBrowser);
    } else {
      const singleUrl = extracted[0];
      if (urlInput) urlInput.value = singleUrl;
      updatePlatformHighlights(singleUrl);
      info = await fetchMediaInfo(singleUrl, cookieBrowser);
    }
    store.set("currentMedia", info);
    renderMediaInfo(info);
    if (info.is_playlist) {
      renderPlaylist(info);
    } else {
      hidePlaylist();
    }
  } catch (err) {
    showError(err);
  } finally {
    setLoading(false);
  }
}

// Download execution logic
async function executeDownload() {
  const currentMedia = store.get("currentMedia");
  if (!currentMedia) return;
  clearError();

  let selectedVideoIds = null;
  if (currentMedia.is_playlist) {
    selectedVideoIds = getSelectedVideoIds();
    if (selectedVideoIds.length === 0) {
      showError("Please select at least one item to download.");
      return;
    }
  }

  const quality = qualitySelect ? qualitySelect.value : "best";
  setDownloadButtonState("downloading", "Starting download...");

  try {
    const cookieBrowser = cookieSelect ? cookieSelect.value : null;
    let downloadDir = folderPathInput ? folderPathInput.value.trim() : null;
    if (downloadDir) {
      downloadDir = downloadDir.replace(/^["']+|["']+$/g, '').trim();
    }

    const res = await startDownload({
      url: currentMedia.url,
      isPlaylist: currentMedia.is_playlist,
      selectedVideoIds,
      batchItems: (currentMedia.platform === "batch") ? currentMedia.items : null,
      playlistTitle: currentMedia.title,
      quality,
      cookieBrowser,
      downloadDir: downloadDir || null,
    });

    showProgressView(res.task_id);
  } catch (err) {
    showError(err);
    setDownloadButtonState("ready");
  }
}

// Initialize Application Orchestrator
function initApp() {
  // 1. Initialize component lifecycle
  initErrorAlert();
  initMediaPreview();
  initPlaylistManager();
  initDownloadTracker({ onRestartDownload: executeDownload });
  initSettings();
  initUpdater({ onRetrySubmission: handleUrlSubmission });

  // 2. Input events & submission
  if (urlForm) {
    urlForm.addEventListener("submit", handleUrlSubmission);
  }
  if (btnSubmit) {
    btnSubmit.addEventListener("click", handleUrlSubmission);
  }

  if (btnBatchToggle) {
    btnBatchToggle.addEventListener("click", () => {
      setBatchMode(!isBatchMode);
    });
  }

  if (urlInput) {
    urlInput.addEventListener("input", (e) => {
      if (btnClear) btnClear.style.display = urlInput.value ? "flex" : "none";
      updatePlatformHighlights(e.target.value);
    });
    urlInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        handleUrlSubmission(e);
      }
    });
  }

  if (batchInput) {
    batchInput.addEventListener("input", () => {
      if (btnClear) btnClear.style.display = batchInput.value ? "flex" : "none";
      const firstUrl = extractUrlsFromText(batchInput.value)[0] || "";
      updatePlatformHighlights(firstUrl);
    });
    batchInput.addEventListener("keydown", (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
        e.preventDefault();
        handleUrlSubmission(e);
      }
    });
  }

  if (btnClear) {
    btnClear.addEventListener("click", () => {
      if (urlInput) urlInput.value = "";
      if (batchInput) batchInput.value = "";
      btnClear.style.display = "none";
      updatePlatformHighlights("");
      if (isBatchMode && batchInput) batchInput.focus();
      else if (urlInput) urlInput.focus();
    });
  }

  // 3. Paste Button Handler with browser clipboard + desktop backend fallback
  if (btnPaste) {
    btnPaste.addEventListener("click", async () => {
      let text = "";
      try {
        if (navigator.clipboard && navigator.clipboard.readText) {
          text = await navigator.clipboard.readText();
        }
      } catch (err) {
        console.warn("Direct clipboard read blocked:", err);
      }

      if (!text || !text.trim()) {
        try {
          text = await getClipboardText();
        } catch (err) {
          console.warn("Backend clipboard fallback notice:", err);
        }
      }

      const trimmed = (text || "").trim();
      if (trimmed) {
        const urls = extractUrlsFromText(trimmed);
        if (urls.length > 1) {
          setBatchMode(true);
          if (batchInput) {
            batchInput.value = urls.join("\n");
            updatePlatformHighlights(urls[0]);
          }
          showToast(`Pasted ${urls.length} links into Batch Mode`, "info", 2000);
        } else if (isBatchMode && batchInput) {
          batchInput.value = trimmed;
          updatePlatformHighlights(trimmed);
          showToast("Pasted batch links from clipboard", "info", 1500);
        } else {
          if (urlInput) {
            urlInput.value = trimmed;
            updatePlatformHighlights(trimmed);
          }
          showToast("Pasted from clipboard", "info", 1500);
        }
        if (btnClear) btnClear.style.display = "flex";
        return;
      }

      const target = (isBatchMode && batchInput) ? batchInput : urlInput;
      if (target) {
        target.focus();
        target.select();
      }
      showToast("Clipboard is empty or inaccessible. Press Ctrl+V to paste.", "info", 3000);
    });
  }

  // 4. Download Trigger Button Handler
  if (btnDownload) {
    btnDownload.addEventListener("click", async () => {
      const currentMedia = store.get("currentMedia");
      if (!currentMedia) return;

      const isDownloaded = store.get("isMediaDownloaded");
      const currentTaskId = store.get("currentTaskId");

      if (isDownloaded && currentTaskId) {
        if (currentMedia.is_playlist) {
          showToast("Opening folder in Windows Explorer...", "info");
          try {
            await openDownloadsFolder(currentTaskId);
          } catch (err) {
            showError(err.message || "Could not open folder in Explorer.");
          }
        } else {
          showToast("Launching media player...", "info");
          try {
            await openFile(currentTaskId);
          } catch (err) {
            console.error("Failed to open file:", err);
            showError(err.message || "Could not launch media player.");
          }
        }
        return;
      }

      executeDownload();
    });
  }

  // 5. Global Keyboard Shortcuts
  window.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (urlInput) urlInput.value = "";
      if (batchInput) batchInput.value = "";
      if (btnClear) btnClear.style.display = "none";
      updatePlatformHighlights("");
      clearError();
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      if (isBatchMode && batchInput) {
        batchInput.focus();
        batchInput.select();
      } else if (urlInput) {
        urlInput.focus();
        urlInput.select();
      }
    }
  });
}

// Start application
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initApp);
} else {
  initApp();
}
