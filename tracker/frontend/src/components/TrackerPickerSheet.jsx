import { DynamicIcon, Close } from '../icons/index.jsx'

// Bottom sheet listing trackers to log. Opened by the FAB (all leaves) or by
// tapping a category tile (that category's leaves). Picking one calls onPick.
export default function TrackerPickerSheet({ open, title = 'Log something', items = [], onPick, onClose }) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-30 mx-auto max-w-[480px]" role="dialog" aria-modal="true" aria-label={title}>
      <button
        aria-label="Close"
        onClick={onClose}
        className="absolute inset-0 h-full w-full bg-black/40"
      />
      <div className="absolute inset-x-0 bottom-0 max-h-[72vh] overflow-y-auto rounded-t-3xl border-t border-line bg-surface p-4 pb-6">
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-line" />
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-display text-[18px] font-extrabold text-ink">{title}</h2>
          <button
            onClick={onClose}
            aria-label="Close"
            className="grid h-8 w-8 place-items-center rounded-full border border-line bg-bg text-ink-2"
          >
            <Close size={16} />
          </button>
        </div>

        {items.length === 0 ? (
          <p className="py-6 text-center font-body text-[13px] text-ink-3">Nothing to log here yet.</p>
        ) : (
          <div className="grid grid-cols-4 gap-3">
            {items.map((t) => (
              <button
                key={t.slug}
                onClick={() => onPick?.(t.slug)}
                className="flex flex-col items-center gap-1.5"
              >
                <span className="grid h-[54px] w-[54px] place-items-center rounded-2xl border border-line bg-bg text-ink-2">
                  <DynamicIcon name={t.icon} size={24} />
                </span>
                <span className="w-full truncate text-center font-body text-[11px] text-ink-3">{t.name}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
