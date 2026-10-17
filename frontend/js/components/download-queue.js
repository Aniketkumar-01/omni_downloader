/**
 * Download Queue Component for OmniDownloader Desktop.
 * Manages FIFO queue storage, UI telemetry rendering, and auto-dispatching.
 */

import { store } from "../state.js";
import { showToast } from "./toast.js";

const queueCard = document.getElementById("queue-card");
const queueCountLabel = document.getElementById("queue-count-label");
const queueItemsList = document.getElementById("queue-items-list");
const btnClearQueue = document.getElementById("btn-clear-queue");

let queue = [];
let onDispatchCallback = null;

export function getQueue() {
  return [...queue];
}

export function getQueueLength() {
  return queue.length;
}

export function hasQueuedTasks() {
  return queue.length > 0;
}

export function enqueueDesktopTask(task) {
  // Prevent exact duplicate identical URLs in queue
  const exists = queue.some(item => item.url === task.url && item.quality === task.quality);
  if (exists) {
    showToast("This video is already in your download queue.", "info");
    return queue.findIndex(item => item.url === task.url && item.quality === task.quality) + 1;
  }

  queue.push(task);
  renderQueue();
  return queue.length;
}

export function pollNextDesktopTask() {
  if (queue.length === 0) return null;
  const next = queue.shift();
  renderQueue();
  return next;
}

export function removeDesktopTask(id) {
  queue = queue.filter(item => item.id !== id);
  renderQueue();
  showToast("Removed from queue.", "info");
}

export function clearDesktopQueue() {
  queue = [];
  renderQueue();
  showToast("Download queue cleared.", "info");
}

export function renderQueue() {
  if (!queueCard || !queueItemsList) return;

  if (queue.length === 0) {
    queueCard.style.display = "none";
    queueItemsList.innerHTML = "";
    return;
  }

  queueCard.style.display = "block";
  if (queueCountLabel) {
    queueCountLabel.textContent = `Pending Queue (${queue.length})`;
  }

  queueItemsList.innerHTML = queue.map((item, index) => {
    const isAudio = item.quality === "audio_mp3";
    const qualityLabel = isAudio ? "Audio (MP3)" : item.quality;
    const playlistSuffix = item.isPlaylist ? " • Playlist" : "";
    return `
      <div class="queue-item-row" data-id="${item.id}">
        <div class="queue-item-left">
          <span class="queue-item-pos">#${index + 1}</span>
          <div class="queue-item-details">
            <span class="queue-item-title" title="${escapeHtml(item.title)}">${escapeHtml(item.title)}</span>
            <span class="queue-item-meta">${escapeHtml(qualityLabel)}${playlistSuffix}</span>
          </div>
        </div>
        <button type="button" class="btn-remove-queue-item" data-remove-id="${item.id}" title="Remove from queue" aria-label="Remove from queue">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    `;
  }).join("");

  // Attach remove handlers
  queueItemsList.querySelectorAll(".btn-remove-queue-item").forEach(btn => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const id = btn.dataset.removeId;
      if (id) removeDesktopTask(id);
    });
  });
}

function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

export function initDownloadQueue(options = {}) {
  if (options.onDispatchNext) {
    onDispatchCallback = options.onDispatchNext;
  }

  if (btnClearQueue) {
    btnClearQueue.addEventListener("click", () => {
      clearDesktopQueue();
    });
  }

  renderQueue();
}
