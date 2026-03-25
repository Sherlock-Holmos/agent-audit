import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export function useTableCardHeight(options = {}) {
  const {
    cardRef,
    bottomOffset = 8,
    minTableHeight = 260,
    initialHeight = 420
  } = options

  const tableHeight = ref(initialHeight)
  let resizeObserver = null

  function resolveBottomOffset() {
    const raw = typeof bottomOffset === 'function' ? bottomOffset() : bottomOffset
    const numeric = Number(raw)
    return Number.isFinite(numeric) ? numeric : 0
  }

  function updateTableHeight() {
    const cardEl = cardRef?.value?.$el || cardRef?.value
    if (!cardEl) return

    const cardStyle = window.getComputedStyle(cardEl)
    const borderTop = Number.parseFloat(cardStyle.borderTopWidth || '0') || 0
    const borderBottom = Number.parseFloat(cardStyle.borderBottomWidth || '0') || 0
    const bodyEl = cardEl.querySelector('.el-card__body')

    let bodyPadding = 0
    if (bodyEl) {
      const bodyStyle = window.getComputedStyle(bodyEl)
      bodyPadding += Number.parseFloat(bodyStyle.paddingTop || '0') || 0
      bodyPadding += Number.parseFloat(bodyStyle.paddingBottom || '0') || 0
    }

    const chromeHeight = borderTop + borderBottom + bodyPadding
    const parentHeight = cardEl.parentElement?.clientHeight || 0

    let available = 0
    if (parentHeight > 0) {
      available = parentHeight - resolveBottomOffset()
    } else {
      const top = cardEl.getBoundingClientRect().top
      const viewportHeight = document.documentElement.clientHeight || window.innerHeight
      available = viewportHeight - top - resolveBottomOffset()
    }

    tableHeight.value = Math.max(minTableHeight, Math.floor(available - chromeHeight))
  }

  function bindAutoResize() {
    nextTick(() => {
      updateTableHeight()
      window.addEventListener('resize', updateTableHeight)
      const cardEl = cardRef?.value?.$el || cardRef?.value
      if (cardEl && window.ResizeObserver) {
        resizeObserver = new ResizeObserver(() => updateTableHeight())
        resizeObserver.observe(cardEl)
      }
    })
  }

  function unbindAutoResize() {
    window.removeEventListener('resize', updateTableHeight)
    if (resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver = null
    }
  }

  onMounted(bindAutoResize)
  onBeforeUnmount(unbindAutoResize)

  return {
    tableHeight,
    updateTableHeight
  }
}
