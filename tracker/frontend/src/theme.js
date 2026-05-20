// Theme persistence. 'light' | 'dark' | 'system' stored in localStorage;
// default 'system' follows prefers-color-scheme until the user picks. The
// `.dark` class on <html> drives all palette variables in theme.css.

const KEY = 'tracker.theme'
const media = () => window.matchMedia('(prefers-color-scheme: dark)')

export function getThemePref() {
  return localStorage.getItem(KEY) || 'system'
}

export function resolveTheme(pref = getThemePref()) {
  if (pref === 'system') return media().matches ? 'dark' : 'light'
  return pref
}

function paint(pref) {
  document.documentElement.classList.toggle('dark', resolveTheme(pref) === 'dark')
}

export function setThemePref(pref) {
  if (pref === 'system') localStorage.removeItem(KEY)
  else localStorage.setItem(KEY, pref)
  paint(pref)
}

// Apply the stored preference and keep 'system' in sync with OS changes.
// Call once at startup (before/at first render).
export function initTheme() {
  paint(getThemePref())
  media().addEventListener('change', () => {
    if (getThemePref() === 'system') paint('system')
  })
}
