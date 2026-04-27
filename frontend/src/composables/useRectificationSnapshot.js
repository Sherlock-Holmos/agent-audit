import { onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchRectificationSnapshot, getRectificationSnapshot } from '../utils/rectificationStore'

export function useRectificationSnapshot() {
  const snapshot = ref(getRectificationSnapshot())
  const loading = ref(false)
  const handleSnapshotUpdated = () => refreshSnapshot({ silent: true })

  async function refreshSnapshot(options = {}) {
    const silent = Boolean(options?.silent)
    const hasCachedData = Boolean((snapshot.value.issues || []).length || (snapshot.value.tasks || []).length || (snapshot.value.reports || []).length)
    if (!silent && !hasCachedData) {
      loading.value = true
    }
    try {
      snapshot.value = await fetchRectificationSnapshot()
      return true
    } catch {
      snapshot.value = getRectificationSnapshot()
      return false
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    refreshSnapshot({ silent: true })
    if (typeof window !== 'undefined') {
      window.addEventListener('rectification-snapshot-updated', handleSnapshotUpdated)
    }
  })

  onBeforeUnmount(() => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('rectification-snapshot-updated', handleSnapshotUpdated)
    }
  })

  return {
    snapshot,
    refreshSnapshot,
    loading
  }
}
