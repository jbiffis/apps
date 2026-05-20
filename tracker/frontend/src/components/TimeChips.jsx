import { useState } from 'react'

// When did it happen? Quick chips + a native datetime picker fallback.
// `value` is a Date; parent passes it straight to occurredAt on save.
const QUICK = [
  { key: 'now', label: 'Just now', at: () => new Date() },
  { key: '15m', label: '15m ago', at: () => new Date(Date.now() - 15 * 60000) },
  { key: '1h', label: '1h ago', at: () => new Date(Date.now() - 60 * 60000) },
  { key: 'yday', label: 'Yesterday', at: () => new Date(Date.now() - 24 * 3600000) },
]

// Format a Date for <input type="datetime-local"> in local time.
function toLocalInput(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function TimeChips({ value, onChange }) {
  const [active, setActive] = useState('now')
  const [picking, setPicking] = useState(false)

  const choose = (key, date) => {
    setActive(key)
    setPicking(key === 'pick')
    onChange(date)
  }

  return (
    <div className="rounded-field border border-line bg-surface px-4 py-3">
      <span className="mb-2 block font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">When</span>
      <div className="flex flex-wrap gap-2">
        {QUICK.map((q) => (
          <button key={q.key} type="button" onClick={() => choose(q.key, q.at())}
            className={`whitespace-nowrap rounded-full px-3 py-1.5 font-body text-[13px] ${
              active === q.key ? 'bg-accent text-white shadow-sm' : 'border border-line bg-surface-2 text-ink-2'
            }`}>
            {q.label}
          </button>
        ))}
        <button type="button" onClick={() => choose('pick', value || new Date())}
          className={`whitespace-nowrap rounded-full px-3 py-1.5 font-body text-[13px] ${
            active === 'pick' ? 'bg-accent text-white shadow-sm' : 'border border-line bg-surface-2 text-ink-2'
          }`}>
          Pick…
        </button>
      </div>
      {picking && (
        <input
          type="datetime-local"
          value={toLocalInput(value || new Date())}
          onChange={(e) => onChange(e.target.value ? new Date(e.target.value) : new Date())}
          className="mt-3 w-full rounded-xl border border-line bg-surface-2 px-3 py-2 font-body text-[14px] text-ink outline-none"
        />
      )}
    </div>
  )
}
