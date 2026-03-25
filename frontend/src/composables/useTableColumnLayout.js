import { ref } from 'vue'

export function useTableColumnLayout(options = {}) {
  const {
    layoutStorageKey = '',
    minByKey = {},
    fixedRowHeight = 44
  } = options

  const columnWidths = ref({})
  const minWidthCache = new Map()

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
    // Always fit to container width so there is no empty gap on the right side.
    return true
  }

  function handleHeaderDragEnd(newWidth, _oldWidth, column) {
    const key = String(column?.columnKey || column?.property || column?.label || '').trim()
    if (!key) return
    const minWidth = resolveHeaderMinWidth(column)
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

  function resolveHeaderMinWidth(column) {
    const label = String(column?.label || '').trim()
    const domMin = resolveHeaderDomMinWidth(column)
    if (!label) {
      return Math.max(100, domMin)
    }

    if (minWidthCache.has(label)) {
      return minWidthCache.get(label)
    }

    let measured = 0
    if (typeof document !== 'undefined') {
      const canvas = resolveHeaderMinWidth._canvas || (resolveHeaderMinWidth._canvas = document.createElement('canvas'))
      const context = canvas.getContext('2d')
      if (context) {
        context.font = '14px sans-serif'
        measured = context.measureText(label).width
      }
    }

    const width = Math.max(100, Math.ceil((measured || label.length * 14) + 56), domMin)
    minWidthCache.set(label, width)
    return width
  }

  function resolveHeaderDomMinWidth(column) {
    if (typeof document === 'undefined') {
      return 0
    }
    const columnId = String(column?.id || '').trim()
    if (!columnId) {
      return 0
    }
    const cell = document.querySelector(`th.${columnId} .cell`)
    if (!cell) {
      return 0
    }
    return Math.ceil(cell.scrollWidth + 24)
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
