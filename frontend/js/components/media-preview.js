/**
 * Media Preview Component for OmniDownloader.
 * Handles thumbnail rendering, platform badges, quality selector, and download trigger button.
 */

import { store } from "../state.js";

const mediaCard = document.getElementById("media-card");
const mediaThumb = document.getElementById("media-thumb");
const mediaDuration = document.getElementById("media-duration");
const mediaBadge = document.getElementById("media-badge");
const platformBadge = document.getElementById("platform-badge");
const mediaTitle = document.getElementById("media-title");
const channelName = document.getElementById("channel-name");
const qualitySelect = document.getElementById("quality-select");
const qualitySizeBadge = document.getElementById("quality-size-badge");
const btnDownload = document.getElementById("btn-download");

export function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(i >= 2 ? 1 : 0)} ${units[i]}`;
}

export function getItemSizeForQuality(item, quality = "best") {
  if (!item) return 0;
  if (item.quality_sizes && item.quality_sizes[quality]) {
    return item.quality_sizes[quality];
  }
  const baseSize = item.filesize || (item.duration ? Math.round(2000 * 1000 / 8 * item.duration) : 0);
  if (!baseSize) return 0;

  const ratios = {
    "best": 1.0,
    "2160p": 2.2,
    "4k": 2.2,
    "1440p": 1.5,
    "2k": 1.5,
    "1080p": 1.0,
    "720p": 0.65,
    "480p": 0.35,
    "360p": 0.25,
    "audio_mp3": 0.15
  };
  const ratio = (ratios[quality] !== undefined) ? ratios[quality] : 1.0;
  return Math.round(baseSize * ratio);
}

export function updateQualityBadge() {
  const currentMedia = store.get("currentMedia");
  if (!qualitySizeBadge || !currentMedia) return;
  const q = qualitySelect ? qualitySelect.value : "best";
  const sizes = currentMedia.quality_sizes_formatted || {};
  const formatted = sizes[q] || currentMedia.filesize_formatted;
  if (formatted) {
    qualitySizeBadge.textContent = formatted;
    qualitySizeBadge.style.display = "inline-flex";
  } else {
    qualitySizeBadge.style.display = "none";
  }
}

export function setDownloadButtonState(state, customText = null) {
  if (!btnDownload) return;

  const currentMedia = store.get("currentMedia");

  if (state === "ready") {
    store.set("isMediaDownloaded", false);
    btnDownload.disabled = false;
    const isPlaylist = currentMedia && currentMedia.is_playlist;
    const selectedCount = document.querySelectorAll(".playlist-checkbox:checked").length;
    const q = qualitySelect ? qualitySelect.value : "best";
    let defaultText = "Download Now";
    if (isPlaylist) {
      const isBatch = currentMedia && currentMedia.platform === "batch";
      const typeName = isBatch ? "Batch" : "Playlist";
      defaultText = `Download ${typeName} (${selectedCount} Selected)`;
    } else if (currentMedia) {
      const singleItem = (currentMedia.items && currentMedia.items[0]) || currentMedia;
      const singleBytes = getItemSizeForQuality(singleItem, q) || (currentMedia.quality_sizes && currentMedia.quality_sizes[q]) || currentMedia.estimated_filesize || currentMedia.filesize;
      const sizeStr = singleBytes > 0 ? ` • ~${formatBytes(singleBytes)}` : "";
      defaultText = `Download Video${sizeStr}`;
    }
    const text = customText || defaultText;
    btnDownload.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
        <polyline points="7 10 12 15 17 10"></polyline>
        <line x1="12" y1="15" x2="12" y2="3"></line>
      </svg>
      <span id="btn-download-text">${text}</span>
    `;
  } else if (state === "downloading") {
    store.set("isMediaDownloaded", false);
    btnDownload.disabled = true;
    const text = customText || "Downloading...";
    btnDownload.innerHTML = `
      <span class="fetch-spinner" style="width: 14px; height: 14px; border-width: 2px; border-color: var(--text-on-accent); border-right-color: transparent;"></span>
      <span id="btn-download-text">${text}</span>
    `;
  } else if (state === "completed") {
    store.set("isMediaDownloaded", true);
    btnDownload.disabled = false;
    const isPlaylist = currentMedia && currentMedia.is_playlist;
    if (isPlaylist) {
      btnDownload.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
        </svg>
        <span id="btn-download-text">Show in Folder</span>
      `;
    } else {
      btnDownload.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="5 3 19 12 5 21 5 3"></polygon>
        </svg>
        <span id="btn-download-text">Open / Play File</span>
      `;
    }
  }
}

export function renderMediaInfo(info) {
  if (!mediaCard) return;

  if (mediaTitle) mediaTitle.textContent = info.title;
  if (channelName) channelName.textContent = info.channel || "Creator";

  if (mediaThumb) {
    if (info.thumbnail) {
      mediaThumb.src = info.thumbnail;
      mediaThumb.style.display = "block";
    } else {
      mediaThumb.style.display = "none";
    }
  }

  // Set platform badge
  if (platformBadge) {
    const platformName = (info.platform || "universal").toUpperCase();
    platformBadge.textContent = platformName;
    if (platformName === "YOUTUBE") {
      platformBadge.style.color = "#ff4b4b";
      platformBadge.style.borderColor = "rgba(255, 75, 75, 0.3)";
      platformBadge.style.background = "rgba(255, 75, 75, 0.1)";
    } else if (platformName === "INSTAGRAM") {
      platformBadge.style.color = "#e1306c";
      platformBadge.style.borderColor = "rgba(225, 48, 108, 0.3)";
      platformBadge.style.background = "rgba(225, 48, 108, 0.1)";
    } else if (platformName === "TIKTOK") {
      platformBadge.style.color = "#00f2fe";
      platformBadge.style.borderColor = "rgba(0, 242, 254, 0.3)";
      platformBadge.style.background = "rgba(0, 242, 254, 0.1)";
    } else if (platformName === "BATCH") {
      platformBadge.style.color = "#a855f7";
      platformBadge.style.borderColor = "rgba(168, 85, 247, 0.4)";
      platformBadge.style.background = "rgba(168, 85, 247, 0.12)";
    } else {
      platformBadge.style.color = "var(--accent-blue)";
      platformBadge.style.borderColor = "rgba(79, 172, 254, 0.3)";
      platformBadge.style.background = "rgba(79, 172, 254, 0.1)";
    }
  }

  // Populate quality options
  if (qualitySelect) {
    qualitySelect.innerHTML = "";
    const sizes = info.quality_sizes_formatted || {};
    if (info.available_qualities && info.available_qualities.length > 0) {
      info.available_qualities.forEach((q) => {
        const opt = document.createElement("option");
        opt.value = q;
        const sizeTag = sizes[q] ? ` (${sizes[q]})` : "";
        if (q === "best") opt.textContent = `Best Quality (MP4)${sizeTag}`;
        else if (q === "2160p" || q === "4k") opt.textContent = `4K 2160p (MP4)${sizeTag}`;
        else if (q === "1440p" || q === "2k") opt.textContent = `2K 1440p (MP4)${sizeTag}`;
        else if (q === "1080p") opt.textContent = `Full HD 1080p (MP4)${sizeTag}`;
        else if (q === "720p") opt.textContent = `HD 720p (MP4)${sizeTag}`;
        else if (q === "480p") opt.textContent = `SD 480p (MP4)${sizeTag}`;
        else if (q === "360p") opt.textContent = `SD 360p (MP4)${sizeTag}`;
        else if (q === "audio_mp3") opt.textContent = `Audio Only (MP3)${sizeTag}`;
        else opt.textContent = `${q.toUpperCase()}${sizeTag}`;
        qualitySelect.appendChild(opt);
      });
    } else {
      const bestTag = sizes["best"] ? ` (${sizes["best"]})` : "";
      const mp3Tag = sizes["audio_mp3"] ? ` (${sizes["audio_mp3"]})` : "";
      qualitySelect.innerHTML = `<option value="best" selected>Best Quality (MP4)${bestTag}</option><option value="audio_mp3">Audio Only (MP3)${mp3Tag}</option>`;
    }
  }

  updateQualityBadge();
  setDownloadButtonState("ready");
  mediaCard.style.display = "block";
  mediaCard.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

export function hideMediaPreview() {
  if (mediaCard) mediaCard.style.display = "none";
}

export function initMediaPreview() {
  if (qualitySelect) {
    qualitySelect.addEventListener("change", (e) => {
      store.set("selectedQuality", e.target.value);
      updateQualityBadge();
      const currentMedia = store.get("currentMedia");
      if (!currentMedia || !currentMedia.is_playlist) {
        setDownloadButtonState("ready");
      }
    });
  }
}
