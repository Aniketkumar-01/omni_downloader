/**
 * Micro-Toasts notification component for OmniDownloader.
 */

const toastContainer = document.getElementById("toast-container");

export function showToast(message, type = "info", duration = 3500) {
  if (!toastContainer) {
    console.log(`[Toast ${type}]: ${message}`);
    return;
  }
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  toastContainer.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transform = "translateY(8px) scale(0.96)";
    toast.style.transition = "all 0.25s ease";
    setTimeout(() => toast.remove(), 250);
  }, duration);
}
