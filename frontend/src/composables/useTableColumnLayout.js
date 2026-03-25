import { ref } from 'vue'

export function useTableColumnLayout(options = {}) {
  const {
    layoutStorageKey = '',
    minByKey = {},
    fixedRowHeight = 44
  } = options

  const columnWidths = ref({})

  function resolveStorageKey() {
    const raw = typeof layoutStorageKey === 'function' ? layoutStorageKey() : layoutStorageKey
    return `${String(raw || '').trim()}:columns`
  }

  function loadColumnWidths() {
    try {
      const cached = localStorage.getItem(resolveStorageKey())
      columnWidths.value = cached ? JSON.parse(cached) : {}
    } catch {
      columnWidths.value = {}
    }
    normalizeLoadedColumnWidths()
  }

  function persistColumnWidths() {
    localStorage.setItem(resolveStorageKey(), JSON.stringify(columnWidths.value))
  }

  function resolveWidth(key, fallback = 80) {
    const width = toWidthNumber(columnWidths.value[key])
    if (width == null) {
      return undefined
    }
    return Math.max(width, fallback)
  }

  function resolveMinWidth(_key, fallback) {
    if (typeof fallback === 'number' && Number.isFinite(fallback)) {
      return fallback
    }
    return undefined
  }

  function shouldAutoFit() {
    // Keep default layout filled, but stop auto-resizing once user customizes widths.
    return Object.keys(columnWidths.value || {}).length === 0
  }

  function handleHeaderDragEnd(newWidth, _oldWidth, column) {
    const key = String(column?.columnKey || column?.property || column?.label || '').trim()
    if (!key) return
    const minWidth = Number(minByKey[key]) || 0
    columnWidths.value = {
      ...columnWidths.value,
      [key]: Math.max(minWidth, Math.round(newWidth || 0))
    }
    persistColumnWidths()
  }

  function resetColumnLayout() {
    columnWidths.value = {}
    localStorage.removeItem(resolveStorageKey())
  }

  function normalizeLoadedColumnWidths() {
    const next = { ...columnWidths.value }
    let changed = false
    Object.entries(minByKey).forEach(([key, min]) => {
      const current = toWidthNumber(next[key])
      if (current == null) {
        if (next[key] !== undefined) {
          delete next[key]
          changed = true
        }
        return
      }
      if (current < min) {
        next[key] = min
        changed = true
      } else if (next[key] !== current) {
        next[key] = current
        changed = true
      }
    })
    if (changed) {
      columnWidths.value = next
      persistColumnWidths()
    }
  }

  function toWidthNumber(value) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value
    }
    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (!trimmed) {
        return null
      }
      const parsed = Number.parseFloat(trimmed)
      return Number.isFinite(parsed) ? parsed : null
    }
    return null
  }

  function rowStyle() {
    return {
      height: `${fixedRowHeight}px`
    }
  }

  function headerRowStyle() {
    return {
      height: `${fixedRowHeight}px`
    }
  }

  return {
    loadColumnWidths,
    resolveWidth,
    resolveMinWidth,
    shouldAutoFit,
    handleHeaderDragEnd,
    resetColumnLayout,
    rowStyle,
    headerRowStyle
  }
}
