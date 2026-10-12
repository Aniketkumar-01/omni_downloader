/**
 * Playlist & Batch Manager Component for OmniDownloader.
 * Manages playlist/batch items list, multi-selection, select-all toggling,
 * and dynamic quality-scaled filesize indicators.
 */

import { store } from "../state.js";
import { formatBytes, getItemSizeForQuality, setDownloadButtonState } from "./media-preview.js";

const playlistSection = document.getElementById("playlist-section");
const playlistCount = document.getElementById("playlist-count");
const playlistToggleBtn = document.getElementById("btn-toggle-playlist");
const playlistItemsList = document.getElementById("playlist-items-list");
const mediaDuration = document.getElementById("media-duration");
const mediaBadge = document.getElementById("media-badge");
const qualitySelect = document.getElementById("quality-select");
const btnDownload = document.getElementById("btn-download");

let allPlaylistSelected = true;

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str || "";
  return div.innerHTML;
}

export function updatePlaylistDownloadButtonCount() {
  const currentMedia = store.get("currentMedia");
  const selectedBoxes = Array.from(document.querySelectorAll(".playlist-checkbox:checked"));
  const selectedCount = selectedBoxes.length;
  const q = qualitySelect ? qualitySelect.value : "best";

  let totalBytes = 0;
  if (currentMedia && currentMedia.items) {
    const itemMap = new Map(currentMedia.items.map(it => [String(it.id), it]));
    selectedBoxes.forEach(cb => {
      const item = itemMap.get(String(cb.value));
      if (item) {
        const itemBytes = getItemSizeForQuality(item, q);
        if (itemBytes) {
          totalBytes += itemBytes;
        }
      }
    });
  }

  const isBatch = currentMedia && currentMedia.platform === "batch";
  const typeName = isBatch ? "Batch" : "Playlist";
  const sizeText = totalBytes > 0 ? ` • ~${formatBytes(totalBytes)}` : "";
  const label = `Download ${typeName} (${selectedCount} Selected${sizeText})`;
  setDownloadButtonState("ready", label);
  if (btnDownload) {
    btnDownload.disabled = selectedCount === 0;
  }
}

export function updatePlaylistItemSizes() {
  const currentMedia = store.get("currentMedia");
  if (!currentMedia || !currentMedia.items) return;
  const q = qualitySelect ? qualitySelect.value : "best";
  const itemMap = new Map(currentMedia.items.map(it => [String(it.id), it]));
  const rows = document.querySelectorAll(".playlist-item");
  rows.forEach(row => {
    const cb = row.querySelector(".playlist-checkbox");
    const sizeSpan = row.querySelector(".playlist-item-size");
    if (!cb || !sizeSpan) return;
    const item = itemMap.get(String(cb.value));
    if (item) {
      const bytes = getItemSizeForQuality(item, q);
      if (bytes > 0) {
        sizeSpan.textContent = `~${formatBytes(bytes)}`;
        sizeSpan.style.display = "inline-block";
      } else {
        sizeSpan.style.display = "none";
      }
    }
  });
}

export function renderPlaylist(info) {
  if (!playlistSection || !playlistItemsList) return;

  const isBatch = (info.platform === "batch");
  if (mediaBadge) {
    mediaBadge.textContent = isBatch ? "BATCH" : "PLAYLIST";
    mediaBadge.style.color = isBatch ? "#a855f7" : "#7928ca";
    mediaBadge.style.borderColor = isBatch ? "rgba(168, 85, 247, 0.4)" : "rgba(121, 40, 202, 0.4)";
    mediaBadge.style.background = isBatch ? "rgba(168, 85, 247, 0.15)" : "rgba(121, 40, 202, 0.15)";
  }
  if (mediaDuration) {
    mediaDuration.textContent = `${info.item_count} Items`;
  }

  if (playlistCount) {
    playlistCount.textContent = isBatch ? `Batch Items (${info.item_count})` : `Playlist Items (${info.item_count})`;
  }
  playlistItemsList.innerHTML = "";

  const currentQ = qualitySelect ? qualitySelect.value : "best";
  allPlaylistSelected = true;
  if (playlistToggleBtn) {
    playlistToggleBtn.textContent = "Deselect All";
  }

  const fragment = document.createDocumentFragment();
  (info.items || []).forEach((item, index) => {
    const row = document.createElement("label");
    row.className = "playlist-item";
    const bytes = getItemSizeForQuality(item, currentQ);
    const sizeStr = bytes > 0 ? `~${formatBytes(bytes)}` : (item.filesize_formatted || "");
    const sizeTag = sizeStr ? `<span class="playlist-item-size">${sizeStr}</span>` : "";
    row.innerHTML = `
      <input type="checkbox" class="playlist-checkbox" value="${item.id}" checked>
      <span class="playlist-item-title">${index + 1}. ${escapeHtml(item.title)}</span>
      <div class="playlist-item-meta">
        ${sizeTag}
        <span class="playlist-item-duration">${item.duration_string || ""}</span>
      </div>
    `;
    fragment.appendChild(row);
  });
  playlistItemsList.appendChild(fragment);

  playlistSection.style.display = "block";
  updatePlaylistDownloadButtonCount();
}

export function hidePlaylist() {
  if (playlistSection) {
    playlistSection.style.display = "none";
  }
  const currentMedia = store.get("currentMedia");
  if (mediaBadge) {
    mediaBadge.textContent = "VIDEO";
    mediaBadge.style.color = "var(--accent-cyan)";
    mediaBadge.style.borderColor = "rgba(0, 242, 254, 0.25)";
    mediaBadge.style.background = "rgba(0, 242, 254, 0.1)";
  }
  if (mediaDuration) {
    const single = currentMedia && currentMedia.items && currentMedia.items[0];
    mediaDuration.textContent = single?.duration_string || "00:00";
  }
  setDownloadButtonState("ready", "Download Now");
}

export function getSelectedVideoIds() {
  const checked = Array.from(document.querySelectorAll(".playlist-checkbox:checked"));
  return checked.map(cb => cb.value);
}

export function initPlaylistManager() {
  if (playlistToggleBtn) {
    playlistToggleBtn.addEventListener("click", () => {
      allPlaylistSelected = !allPlaylistSelected;
      const checkboxes = document.querySelectorAll(".playlist-checkbox");
      checkboxes.forEach((cb) => (cb.checked = allPlaylistSelected));
      playlistToggleBtn.textContent = allPlaylistSelected ? "Deselect All" : "Select All";
      updatePlaylistDownloadButtonCount();
      if (store.get("isMediaDownloaded")) {
        setDownloadButtonState("ready");
      }
    });
  }

  if (playlistItemsList) {
    playlistItemsList.addEventListener("change", (e) => {
      if (e.target.classList.contains("playlist-checkbox")) {
        updatePlaylistDownloadButtonCount();
        if (store.get("isMediaDownloaded")) {
          setDownloadButtonState("ready");
        }
      }
    });
  }

  // Hook quality changes into playlist sizes
  if (qualitySelect) {
    qualitySelect.addEventListener("change", () => {
      const currentMedia = store.get("currentMedia");
      if (currentMedia && currentMedia.is_playlist) {
        updatePlaylistItemSizes();
        updatePlaylistDownloadButtonCount();
      }
    });
  }
}
