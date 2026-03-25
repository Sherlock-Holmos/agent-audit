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
    const knownKeys = Object.keys(minByKey || {})
    let key = ''

    if (knownKeys.length > 0) {
      key = resolveKnownColumnKey(column, knownKeys)
      if (!key) {
        return
      }
    } else {
      key = String(column?.columnKey || column?.property || column?.label || '').trim()
    }

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

    const knownKeys = Object.keys(minByKey || {})
    if (knownKeys.length > 0) {
      Object.keys(next).forEach((key) => {
        if (!Object.prototype.hasOwnProperty.call(minByKey, key)) {
          delete next[key]
          changed = true
        }
      })
    }

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

  function resolveKnownColumnKey(column, knownKeys) {
    const candidates = [
      column?.columnKey,
      column?.rawColumnKey,
      column?.property,
      column?.label
    ].map((it) => String(it || '').trim()).filter(Boolean)

    const direct = candidates.find((candidate) => Object.prototype.hasOwnProperty.call(minByKey, candidate))
    if (direct) {
      return direct
    }

    const columnId = String(column?.id || '').trim()
    if (!columnId) {
      return ''
    }

    const allMatches = [...columnId.matchAll(/column_(\d+)/g)]
    if (allMatches.length === 0) {
      return ''
    }

    const last = allMatches[allMatches.length - 1]
    const index = Number.parseInt(last[1], 10) - 1
    if (!Number.isInteger(index) || index < 0 || index >= knownKeys.length) {
      return ''
    }
    return knownKeys[index]
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
