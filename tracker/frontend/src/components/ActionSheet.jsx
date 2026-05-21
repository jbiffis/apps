// Minimal context menu / action sheet. Renders a list of actions; `danger`
// styles destructive ones. Tapping the backdrop or an action closes it.
export default function ActionSheet({ title, actions = [], onClose }) {
  return (
    <div className="fixed inset-0 z-30 mx-auto max-w-[480px]" role="dialog" aria-modal="true" aria-label={title || 'Actions'}>
      <button aria-label="Close" onClick={onClose} className="absolute inset-0 h-full w-full bg-black/40" />
      <div className="absolute inset-x-3 bottom-3 overflow-hidden rounded-2xl border border-line bg-surface">
        {title && (
          <p className="border-b border-line px-4 py-2.5 text-center font-body text-[12px] text-ink-3">{title}</p>
        )}
        {actions.map((a, i) => (
          <button
            key={a.label}
            onClick={() => { onClose?.(); a.onSelect?.() }}
            className={`block w-full px-4 py-3.5 text-center font-display text-[15px] font-bold ${
              i > 0 ? 'border-t border-line' : ''
            } ${a.danger ? 'text-warn' : 'text-ink'}`}
          >
            {a.label}
          </button>
        ))}
        <button
          onClick={onClose}
          className="block w-full border-t-[6px] border-bg px-4 py-3.5 text-center font-display text-[15px] font-bold text-ink-3"
        >
          Cancel
        </button>
      </div>
    </div>
  )
}
