/**
 * Error Alert Component for OmniDownloader.
 * Displays categorized error banners with layman advice and recovery actions.
 */

import { formatLaymanError } from "../utils/errors.js";

const errorAlert = document.getElementById("error-alert");
const errorSourceBadge = document.getElementById("error-source-badge");
const errorSourceIcon = document.getElementById("error-source-icon");
const errorSourceText = document.getElementById("error-source-text");
const errorCodeChip = document.getElementById("error-code-chip");
const errorTitle = document.getElementById("error-title");
const errorMessage = document.getElementById("error-message");
const errorHintBox = document.getElementById("error-hint-box");
const errorHintText = document.getElementById("error-hint-text");
const errorActionRow = document.getElementById("error-action-row");
const errorTechDetails = document.getElementById("error-tech-details");
const errorRawText = document.getElementById("error-raw-text");
const btnDismissError = document.getElementById("btn-dismiss-error");

export function initErrorAlert() {
  if (btnDismissError) {
    btnDismissError.addEventListener("click", clearError);
  }
}

export function clearError() {
  if (!errorAlert) return;
  errorAlert.style.display = "none";
  if (errorMessage) errorMessage.textContent = "";
  if (errorHintBox) errorHintBox.style.display = "none";
  if (errorActionRow) errorActionRow.style.display = "none";
  if (errorTechDetails) errorTechDetails.style.display = "none";
}

export function showError(errOrMsg) {
  if (!errorAlert) return;

  const info = formatLaymanError(errOrMsg);

  if (errorTitle) errorTitle.textContent = info.title;
  if (errorMessage) errorMessage.textContent = info.message;
  if (errorCodeChip) errorCodeChip.textContent = info.code;

  if (errorSourceBadge && errorSourceText && errorSourceIcon) {
    if (info.source === "platform") {
      errorSourceBadge.className = "error-source-badge source-platform";
      errorSourceText.textContent = "Platform Issue";
      errorSourceIcon.innerHTML = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`;
    } else if (info.source === "network") {
      errorSourceBadge.className = "error-source-badge source-network";
      errorSourceText.textContent = "Network Issue";
      errorSourceIcon.innerHTML = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 12.55a11 11 0 0 1 14.08 0"></path><path d="M1.42 9a16 16 0 0 1 21.16 0"></path><path d="M8.53 16.11a6 6 0 0 1 6.95 0"></path><line x1="12" y1="20" x2="12.01" y2="20"></line></svg>`;
    } else {
      errorSourceBadge.className = "error-source-badge source-app";
      errorSourceText.textContent = "Application Notice";
      errorSourceIcon.innerHTML = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>`;
    }
  }

  if (errorHintBox && errorHintText) {
    if (info.hint) {
      errorHintText.textContent = info.hint;
      errorHintBox.style.display = "flex";
    } else {
      errorHintBox.style.display = "none";
    }
  }

  if (errorActionRow) {
    errorActionRow.style.display = info.code === "ENGINE_OUTDATED" ? "flex" : "none";
  }

  if (errorTechDetails && errorRawText) {
    if (info.rawText && info.rawText !== info.message) {
      errorRawText.textContent = info.rawText;
      errorTechDetails.style.display = "block";
    } else {
      errorTechDetails.style.display = "none";
    }
  }

  errorAlert.style.display = "flex";
  errorAlert.scrollIntoView({ behavior: "smooth", block: "nearest" });
}
