import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    isCollapse: true
  }),
  actions: {
    toggleCollapse() {
      this.isCollapse = !this.isCollapse
    },
    setCollapse(val: boolean) {
      this.isCollapse = val
    }
  }
})
