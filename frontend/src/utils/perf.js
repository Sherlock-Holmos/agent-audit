export function createRafThrottle(fn) {
  let rafId = 0

  const throttled = (...args) => {
    if (rafId) return
    rafId = window.requestAnimationFrame(() => {
      rafId = 0
      fn(...args)
    })
  }

  throttled.cancel = () => {
    if (!rafId) return
    window.cancelAnimationFrame(rafId)
    rafId = 0
  }

  return throttled
}
