import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getThemePref, resolveTheme, setThemePref } from '../theme.js'
import { clearSession, getUser } from '../auth.js'
import { useApi } from '../hooks/useApi.js'
import { flattenLeaves, leavesUnder } from '../lib/catalog.js'
import { greeting, dateLabel, timeLabel, summarizeOptions } from '../lib/format.js'
import AppShell from '../components/AppShell.jsx'
import BottomNav from '../components/BottomNav.jsx'
import TrackerPickerSheet from '../components/TrackerPickerSheet.jsx'
import { Sun, Logout, Plus, DynamicIcon } from '../icons/index.jsx'

const EMPTY = []

export default function Home() {
  const navigate = useNavigate()
  const user = getUser()
  const [pref, setPref] = useState(getThemePref())
  const [sheet, setSheet] = useState({ open: false, title: '', items: [] })

  const hero = useApi('/home/hero')
  const today = useApi('/home/today')
  const catalog = useApi('/event-types')
  const tree = catalog.data || EMPTY
  // The today/logged-event payload's eventType carries only {slug, name};
  // resolve its icon from the catalog we already fetched.
  const iconBySlug = useMemo(() => {
    const m = {}
    flattenLeaves(tree).forEach((l) => { m[l.slug] = l.icon })
    return m
  }, [tree])

  function cycleTheme() {
    const next = resolveTheme(pref) === 'dark' ? 'light' : 'dark'
    setThemePref(next)
    setPref(next)
  }
  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }
  function logTracker(slug) {
    setSheet({ open: false, title: '', items: [] })
    navigate(`/log/${slug}`)
  }
  function openTile(node) {
    if (node.isCategory) setSheet({ open: true, title: node.name, items: leavesUnder(node) })
    else logTracker(node.slug)
  }
  function openFab() {
    setSheet({ open: true, title: 'Log something', items: flattenLeaves(tree) })
  }

  const bar = (
    <header className="flex items-start justify-between px-[18px] pb-2.5 pt-3">
      <div>
        <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">{dateLabel()}</p>
        <h1 className="font-display text-[26px] font-extrabold leading-tight text-ink">
          {greeting()}{user?.displayName ? `, ${user.displayName}.` : '.'}
        </h1>
      </div>
      <div className="flex items-center gap-2 pt-1">
        <button onClick={cycleTheme} aria-label="Toggle theme"
          className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2">
          <Sun size={18} />
        </button>
        <button onClick={logout} aria-label="Sign out"
          className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2">
          <Logout size={18} />
        </button>
      </div>
    </header>
  )

  return (
    <AppShell bar={bar} nav={<BottomNav active="home" onSelect={(k) => k === 'log' && openFab()} />}>
      {/* Hero cards */}
      <section className="space-y-3">
        {hero.loading && <CardSkeleton />}
        {hero.data?.map((c, i) => (
          <HeroCard key={c.eventTypeSlug} card={c} primary={c.primary ?? i === 0} onClick={() => logTracker(c.eventTypeSlug)} />
        ))}
      </section>

      {/* All trackers */}
      <section className="mt-6 space-y-3">
        <h2 className="font-display text-[15px] font-bold text-ink">All trackers</h2>
        {catalog.loading ? (
          <p className="font-body text-[13px] text-ink-3">Loading…</p>
        ) : (
          <div className="grid grid-cols-4 gap-3">
            {tree.map((node) => (
              <button key={node.slug} onClick={() => openTile(node)} className="flex flex-col items-center gap-1.5">
                <span className="grid h-[54px] w-[54px] place-items-center rounded-2xl border border-line bg-surface text-ink-2">
                  <DynamicIcon name={node.icon} size={24} />
                </span>
                <span className="w-full truncate text-center font-body text-[11px] text-ink-3">{node.name}</span>
              </button>
            ))}
          </div>
        )}
      </section>

      {/* Today */}
      <section className="mt-6 space-y-3">
        <h2 className="font-display text-[15px] font-bold text-ink">Today</h2>
        {today.loading ? (
          <p className="font-body text-[13px] text-ink-3">Loading…</p>
        ) : today.data?.length ? (
          <ul className="space-y-2">
            {today.data.map((e) => (
              <li key={e.id} className="flex items-center gap-3 rounded-2xl border border-line bg-surface px-3 py-2.5">
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-accent-2 text-accent-ink">
                  <DynamicIcon name={iconBySlug[e.eventType?.slug]} size={18} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-body text-[13px] font-semibold text-ink">{e.eventType?.name || e.eventType?.slug}</p>
                  {(summarizeOptions(e.options) || e.note) && (
                    <p className="truncate font-body text-[11px] text-ink-3">{summarizeOptions(e.options) || e.note}</p>
                  )}
                </div>
                <span className="shrink-0 font-mono text-[10px] text-ink-3">{timeLabel(e.occurredAt)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="font-body text-[13px] text-ink-3">Nothing logged yet today. Tap + to start.</p>
        )}
      </section>

      {/* FAB */}
      <button
        onClick={openFab}
        aria-label="Log something"
        className="fixed bottom-[84px] right-[calc(50%-240px+18px)] z-20 grid h-[58px] w-[58px] place-items-center rounded-[20px] text-white shadow-lg max-[480px]:right-[18px]"
        style={{ background: 'linear-gradient(160deg, var(--accent), var(--accent-deep))' }}
      >
        <Plus size={26} />
      </button>

      <TrackerPickerSheet
        open={sheet.open}
        title={sheet.title}
        items={sheet.items}
        onPick={logTracker}
        onClose={() => setSheet({ open: false, title: '', items: [] })}
      />
    </AppShell>
  )
}

function HeroCard({ card, primary, onClick }) {
  const pct = Math.round((card.progress ?? 0) * 100)
  return (
    <button
      onClick={onClick}
      className={`qcard flex w-full flex-col justify-between text-left ${primary ? 'qcard-primary' : ''}`}
      style={primary ? { boxShadow: '0 10px 24px -12px var(--accent-deep)' } : undefined}
    >
      <div className="flex items-start justify-between">
        <span className={`grid h-[34px] w-[34px] place-items-center rounded-xl ${primary ? 'bg-white/20' : 'bg-accent-2 text-accent-ink'}`}>
          <DynamicIcon name={card.icon} size={20} />
        </span>
        {card.captionText && (
          <span className={`font-mono text-[10px] uppercase tracking-[0.08em] ${primary ? 'opacity-80' : 'text-ink-3'}`}>
            {card.captionText}
          </span>
        )}
      </div>
      <div className="mt-3">
        <p className={`font-body text-[13px] ${primary ? 'opacity-90' : 'text-ink-2'}`}>{card.name}</p>
        <p className={`font-display text-[22px] font-extrabold leading-none ${primary ? '' : 'text-ink'}`}>
          {card.valueText}{' '}
          <span className={`text-[15px] font-bold ${primary ? 'opacity-80' : 'text-ink-3'}`}>{card.subText}</span>
        </p>
        <div className={`mt-2 h-[5px] w-full overflow-hidden rounded-full ${primary ? 'bg-white/25' : 'bg-surface-2'}`}>
          <div className={`h-full rounded-full ${primary ? 'bg-white' : 'bg-accent'}`} style={{ width: `${pct}%` }} />
        </div>
      </div>
    </button>
  )
}

function CardSkeleton() {
  return <div className="qcard animate-pulse bg-surface-2" />
}
