import { defineStore } from 'pinia'

const COLLAPSE_KEY = 'app_sidebar_collapsed'
const THEME_MODE_KEY = 'app_theme_mode'

type ThemeMode = 'light' | 'dark' | 'system'

function getInitialCollapseState() {
  const value = localStorage.getItem(COLLAPSE_KEY)
  if (value === null) return true
  return value === 'true'
}

function persistCollapseState(value: boolean) {
  localStorage.setItem(COLLAPSE_KEY, String(value))
}

function getInitialThemeMode(): ThemeMode {
  const value = localStorage.getItem(THEME_MODE_KEY)
  if (value === 'light' || value === 'dark' || value === 'system') {
    return value
  }
  return 'system'
}

function persistThemeMode(value: ThemeMode) {
  localStorage.setItem(THEME_MODE_KEY, value)
}

function resolveIsDark(mode: ThemeMode) {
  if (mode === 'dark') return true
  if (mode === 'light') return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export const useAppStore = defineStore('app', {
  state: () => ({
    isCollapse: getInitialCollapseState(),
    themeMode: getInitialThemeMode() as ThemeMode
  }),
  actions: {
    toggleCollapse() {
      this.isCollapse = !this.isCollapse
      persistCollapseState(this.isCollapse)
    },
    setCollapse(val: boolean) {
      this.isCollapse = val
      persistCollapseState(this.isCollapse)
    },
    setThemeMode(mode: ThemeMode) {
      this.themeMode = mode
      persistThemeMode(mode)
      this.applyTheme()
    },
    applyTheme() {
      const isDark = resolveIsDark(this.themeMode)
      document.documentElement.classList.toggle('dark-theme', isDark)
    }
  }
})
