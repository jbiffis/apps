import { useRef } from 'react'

// Fire `onLongPress` after holding ~500ms (touch or mouse), and also on
// right-click (contextmenu). Cancels if the pointer moves or releases early.
// Spread the returned props onto the target element.
export function useLongPress(onLongPress, { delay = 500 } = {}) {
  const timer = useRef(null)
  const fired = useRef(false)

  const start = (e) => {
    fired.current = false
    timer.current = setTimeout(() => {
      fired.current = true
      onLongPress(e)
    }, delay)
  }
  const cancel = () => {
    if (timer.current) clearTimeout(timer.current)
    timer.current = null
  }

  return {
    onPointerDown: start,
    onPointerUp: cancel,
    onPointerLeave: cancel,
    onPointerMove: cancel,
    onContextMenu: (e) => {
      e.preventDefault()
      cancel()
      onLongPress(e)
    },
    // Swallow the click that follows a long-press so it doesn't also navigate.
    onClickCapture: (e) => {
      if (fired.current) {
        e.stopPropagation()
        e.preventDefault()
        fired.current = false
      }
    },
  }
}
