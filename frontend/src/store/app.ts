import { defineStore } from 'pinia'

const COLLAPSE_KEY = 'app_sidebar_collapsed'

function getInitialCollapseState() {
  const value = localStorage.getItem(COLLAPSE_KEY)
  if (value === null) return true
  return value === 'true'
}

function persistCollapseState(value: boolean) {
  localStorage.setItem(COLLAPSE_KEY, String(value))
}

export const useAppStore = defineStore('app', {
  state: () => ({
    isCollapse: getInitialCollapseState()
  }),
  actions: {
    toggleCollapse() {
      this.isCollapse = !this.isCollapse
      persistCollapseState(this.isCollapse)
    },
    setCollapse(val: boolean) {
      this.isCollapse = val
      persistCollapseState(this.isCollapse)
    }
  }
})
