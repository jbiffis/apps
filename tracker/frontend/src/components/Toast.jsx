import { useEffect, useState } from 'react'
import { Check } from '../icons/index.jsx'

// Transient confirmation with an optional Undo. Auto-dismisses after `duration`
// ms; calling onUndo is the caller's chance to reverse the action (e.g. DELETE
// the just-created entry). Sits above the bottom nav.
export default function Toast({ message, onUndo, onDismiss, duration = 5000 }) {
  const [leaving, setLeaving] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => {
      setLeaving(true)
      setTimeout(onDismiss, 200)
    }, duration)
    return () => clearTimeout(t)
  }, [duration, onDismiss])

  return (
    <div
      role="status"
      className={`fixed inset-x-[18px] bottom-[92px] z-40 mx-auto flex max-w-[444px] items-center gap-3 rounded-2xl bg-ink px-3 py-2.5 text-bg shadow-lg transition-opacity duration-200 ${
        leaving ? 'opacity-0' : 'opacity-100'
      }`}
    >
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-accent text-white">
        <Check size={18} />
      </span>
      <span className="flex-1 font-body text-[13px]">{message}</span>
      {onUndo && (
        <button
          onClick={() => { setLeaving(true); onUndo() }}
          className="shrink-0 font-display text-[13px] font-bold text-accent"
        >
          Undo
        </button>
      )}
    </div>
  )
}
