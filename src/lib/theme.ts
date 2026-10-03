/** Dark is the default; the light theme is a per-device choice. index.html applies it before first paint. */

export type Theme = 'dark' | 'light';

export const THEME_KEY = 'szokert.theme'; // keep in sync with the inline script in index.html
export const THEME_COLORS: Record<Theme, string> = { dark: '#0f1512', light: '#f6f8f2' }; // = --bg

type Storage = Pick<globalThis.Storage, 'getItem' | 'setItem'>;

function defaultStorage(): Storage | undefined {
  try {
    return globalThis.localStorage;
  } catch {
    return undefined;
  }
}

export function storedTheme(storage: Storage | undefined = defaultStorage()): Theme {
  try {
    return storage?.getItem(THEME_KEY) === 'light' ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
}

export function saveTheme(theme: Theme, storage: Storage | undefined = defaultStorage()): void {
  try {
    storage?.setItem(THEME_KEY, theme);
  } catch {
    // Storage unavailable (private mode): the choice lasts for this page only.
  }
}

/** Set the theme on <html> and the browser-bar colour. */
export function applyTheme(theme: Theme, doc: Document = document): void {
  doc.documentElement.dataset.theme = theme;
  doc.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
}
