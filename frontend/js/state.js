/**
 * Reactive State Management Store for OmniDownloader frontend.
 * Provides centralized state and pub/sub event subscription.
 */

class AppStore {
  constructor() {
    this._state = {
      currentMedia: null,
      isBatchMode: false,
      selectedQuality: "best",
      selectedPlaylistIds: new Set(),
      activeTaskId: null,
      activeTaskStatus: null,
      isDownloadPaused: false,
      isMediaDownloaded: false,
      downloadDir: "",
      engineVersion: "",
      appVersion: "1.4.7",
      cookiesInstalled: false,
      cookieBrowser: "none"
    };

    this._listeners = new Map();
  }

  get(key) {
    return this._state[key];
  }

  getState() {
    return { ...this._state };
  }

  set(key, value) {
    const oldValue = this._state[key];
    if (oldValue === value) return;

    this._state[key] = value;
    this._notify(key, value, oldValue);
  }

  update(partial) {
    for (const [key, val] of Object.entries(partial)) {
      this.set(key, val);
    }
  }

  subscribe(key, callback) {
    if (!this._listeners.has(key)) {
      this._listeners.set(key, new Set());
    }
    this._listeners.get(key).add(callback);

    // Return unbind function
    return () => {
      const set = this._listeners.get(key);
      if (set) set.delete(callback);
    };
  }

  _notify(key, value, oldValue) {
    const listeners = this._listeners.get(key);
    if (listeners) {
      listeners.forEach((cb) => {
        try {
          cb(value, oldValue);
        } catch (err) {
          console.error(`Error in store subscriber for "${key}":`, err);
        }
      });
    }

    // Global wildcard subscriber
    const allListeners = this._listeners.get("*");
    if (allListeners) {
      allListeners.forEach((cb) => {
        try {
          cb(key, value, oldValue);
        } catch (err) {
          console.error(`Error in global store subscriber:`, err);
        }
      });
    }
  }
}

export const store = new AppStore();
