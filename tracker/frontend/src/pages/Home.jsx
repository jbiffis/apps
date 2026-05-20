import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getThemePref, resolveTheme, setThemePref } from '../theme.js'
import { clearSession, getUser } from '../auth.js'
import {
  Home as HomeIcon, Plus, Stats, User, Sun, Logout,
  Sleep, Pill, Water, DynamicIcon,
} from '../icons/index.jsx'

// Epic 5/6 placeholder Home. Proves tokens, typography, icons, dark mode and
// logout. Real data wiring lands in Epic 7.

const TILES = [
  { name: 'Medication', icon: 'Pill' },
  { name: 'Water', icon: 'Water' },
  { name: 'Sleep', icon: 'Sleep' },
  { name: 'Mood', icon: 'Mood' },
  { name: 'Workout', icon: 'Workout' },
  { name: 'Coffee', icon: 'Coffee' },
  { name: 'Journal', icon: 'Journal' },
  { name: 'Weight', icon: 'Weight' },
]

function NavItem({ icon: Icon, label, active }) {
  return (
    <button
      className={`flex flex-col items-center gap-1 px-3 py-1.5 rounded-full font-body text-[11px] ${
        active ? 'bg-accent-2 text-accent-ink' : 'text-ink-3'
      }`}
    >
      <Icon size={22} />
      {label}
    </button>
  )
}

export default function Home() {
  const navigate = useNavigate()
  const [pref, setPref] = useState(getThemePref())
  const user = getUser()

  function cycleTheme() {
    const next = resolveTheme(pref) === 'dark' ? 'light' : 'dark'
    setThemePref(next)
    setPref(next)
  }

  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="mx-auto flex min-h-full max-w-[480px] flex-col bg-bg">
      {/* App bar */}
      <header className="flex items-center justify-between px-[18px] pb-2.5 pt-2">
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">LifeTracker</p>
          <h1 className="font-display text-[22px] font-extrabold text-ink">
            {user?.displayName ? `Hi, ${user.displayName}.` : 'Good morning.'}
          </h1>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={cycleTheme}
            aria-label="Toggle theme"
            className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2"
          >
            <Sun size={18} />
          </button>
          <button
            onClick={logout}
            aria-label="Sign out"
            className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2"
          >
            <Logout size={18} />
          </button>
        </div>
      </header>

      <main className="flex-1 space-y-5 px-[18px] pb-24">
        {/* Hero card (primary gradient) */}
        <section
          className="qcard qcard-primary flex flex-col justify-between"
          style={{ boxShadow: '0 10px 24px -12px var(--accent-deep)' }}
        >
          <div className="flex items-start justify-between">
            <div className="grid h-[34px] w-[34px] place-items-center rounded-xl bg-white/20">
              <Pill size={20} />
            </div>
            <span className="font-mono text-[10px] uppercase tracking-[0.08em] opacity-80">1 left</span>
          </div>
          <div>
            <p className="font-body text-[13px] opacity-90">Medication</p>
            <p className="font-display text-[22px] font-extrabold leading-none">
              2 <span className="text-[15px] font-bold opacity-80">/3</span>
            </p>
            <div className="mt-2 h-[5px] w-full overflow-hidden rounded-full bg-white/25">
              <div className="h-full rounded-full bg-white" style={{ width: '66%' }} />
            </div>
          </div>
        </section>

        {/* Two secondary hero cards */}
        <section className="grid grid-cols-2 gap-3">
          {[
            { Icon: Water, name: 'Water', value: '5', sub: '/8', cap: 'glasses', pct: '62%' },
            { Icon: Sleep, name: 'Sleep', value: '7.2', sub: 'h', cap: 'last night', pct: '90%' },
          ].map(({ Icon, name, value, sub, cap, pct }) => (
            <div key={name} className="qcard flex flex-col justify-between">
              <div className="grid h-[34px] w-[34px] place-items-center rounded-xl bg-accent-2 text-accent-ink">
                <Icon size={20} />
              </div>
              <div>
                <p className="font-body text-[13px] text-ink-2">{name}</p>
                <p className="font-display text-[22px] font-extrabold leading-none text-ink">
                  {value} <span className="text-[15px] font-bold text-ink-3">{sub}</span>
                </p>
                <p className="font-mono text-[10px] text-ink-3">{cap}</p>
                <div className="mt-2 h-[5px] w-full overflow-hidden rounded-full bg-surface-2">
                  <div className="h-full rounded-full bg-accent" style={{ width: pct }} />
                </div>
              </div>
            </div>
          ))}
        </section>

        {/* All trackers grid */}
        <section className="space-y-3">
          <h2 className="font-display text-[15px] font-bold text-ink">All trackers</h2>
          <div className="grid grid-cols-4 gap-3">
            {TILES.map((t) => (
              <button key={t.name} className="flex flex-col items-center gap-1.5">
                <span className="grid h-[54px] w-[54px] place-items-center rounded-2xl border border-line bg-surface text-ink-2">
                  <DynamicIcon name={t.icon} size={24} />
                </span>
                <span className="font-body text-[11px] text-ink-3">{t.name}</span>
              </button>
            ))}
          </div>
        </section>
      </main>

      {/* Bottom nav */}
      <nav className="sticky bottom-0 flex items-center justify-around border-t border-line bg-bg px-2 py-2">
        <NavItem icon={HomeIcon} label="Home" active />
        <NavItem icon={Plus} label="Log" />
        <NavItem icon={Stats} label="Stats" />
        <NavItem icon={User} label="Me" />
      </nav>
    </div>
  )
}
