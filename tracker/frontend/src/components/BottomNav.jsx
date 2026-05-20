import { Home, Plus, Stats, User } from '../icons/index.jsx'

const TABS = [
  { key: 'home', label: 'Home', icon: Home },
  { key: 'log', label: 'Log', icon: Plus },
  { key: 'stats', label: 'Stats', icon: Stats },
  { key: 'me', label: 'Me', icon: User },
]

// Phase 1 wires only the Home tab; the others are inert placeholders (Stats/Me
// land in Phase 2, Log opens the picker via the FAB).
export default function BottomNav({ active = 'home', onSelect }) {
  return (
    <nav className="sticky bottom-0 z-10 flex items-center justify-around border-t border-line bg-bg px-2 py-2">
      {TABS.map(({ key, label, icon: Icon }) => {
        const on = key === active
        return (
          <button
            key={key}
            onClick={() => onSelect?.(key)}
            aria-label={label}
            aria-current={on ? 'page' : undefined}
            className={`flex flex-col items-center gap-1 rounded-full px-3 py-1.5 font-body text-[11px] ${
              on ? 'bg-accent-2 text-accent-ink' : 'text-ink-3'
            }`}
          >
            <Icon size={22} />
            {label}
          </button>
        )
      })}
    </nav>
  )
}
