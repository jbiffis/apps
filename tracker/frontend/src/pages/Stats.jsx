import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApi } from '../hooks/useApi.js'
import AppShell from '../components/AppShell.jsx'
import BottomNav from '../components/BottomNav.jsx'
import { DynamicIcon } from '../icons/index.jsx'

const WEEKS = 12
const DAYS = WEEKS * 7

// IANA zone (e.g. "America/Toronto") — passed to the backend so day buckets
// align to the user's local midnight instead of UTC.
const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone

function level(count) {
  if (!count) return 0
  if (count < 3) return 1
  if (count < 6) return 2
  if (count < 10) return 3
  return 4
}

// 'YYYY-MM-DD' for a Date in the user's local zone — must match the bucket
// keys the backend emits for `tz=${TZ}`.
function localIso(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export default function Stats() {
  const navigate = useNavigate()
  const { data, loading } = useApi(`/stats?days=${DAYS}&tz=${encodeURIComponent(TZ)}`)

  // 'YYYY-MM-DD' (caller's tz) -> count, plus the trailing DAYS-day cell list.
  const byDay = useMemo(() => {
    const m = new Map()
    for (const d of data?.daily || []) m.set(d.date, d.count)
    return m
  }, [data])

  const cells = useMemo(() => {
    const out = []
    const today = new Date()
    for (let i = DAYS - 1; i >= 0; i--) {
      const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i)
      const iso = localIso(d)
      out.push({ iso, count: byDay.get(iso) || 0 })
    }
    return out
  }, [byDay])

  const perTracker = data?.perTracker || []
  const maxCount = perTracker.length ? perTracker[0].count : 0

  const onNav = (k) => { if (k === 'home' || k === 'log') navigate('/'); else if (k === 'me') navigate('/me') }

  const bar = (
    <header className="flex items-center px-[18px] pb-2.5 pt-3">
      <h1 className="font-display text-[22px] font-extrabold text-ink">Stats</h1>
    </header>
  )

  return (
    <AppShell bar={bar} nav={<BottomNav active="stats" onSelect={onNav} />}>
      {loading ? (
        <p className="font-body text-[13px] text-ink-3">Loading…</p>
      ) : (
        <>
          {/* Summary */}
          <section className="grid grid-cols-3 gap-3">
            <Stat label="Entries" value={data?.totalEntries ?? 0} sub={`last ${WEEKS} wks`} />
            <Stat label="Streak" value={data?.currentStreakDays ?? 0} sub="days now" primary />
            <Stat label="Best" value={data?.longestStreakDays ?? 0} sub="day streak" />
          </section>

          {/* Activity heatmap */}
          <section className="mt-6 space-y-2">
            <h2 className="font-display text-[15px] font-bold text-ink">Activity</h2>
            <div className="overflow-x-auto rounded-qcard border border-line bg-surface p-3">
              <div className="grid grid-flow-col grid-rows-7 gap-1" style={{ width: 'max-content' }}>
                {cells.map((c) => (
                  <div
                    key={c.iso}
                    title={`${c.iso}: ${c.count}`}
                    className={`h-3.5 w-3.5 rounded-[3px] ${c.count ? '' : 'bg-surface-2'}`}
                    style={c.count ? { backgroundColor: 'var(--accent)', opacity: 0.25 + level(c.count) * 0.18 } : undefined}
                  />
                ))}
              </div>
            </div>
          </section>

          {/* Top trackers */}
          <section className="mt-6 space-y-2">
            <h2 className="font-display text-[15px] font-bold text-ink">Most logged</h2>
            {perTracker.length === 0 ? (
              <p className="font-body text-[13px] text-ink-3">Nothing logged in this window yet.</p>
            ) : (
              <ul className="space-y-2">
                {perTracker.slice(0, 12).map((t) => (
                  <li key={t.eventTypeSlug} className="flex items-center gap-3">
                    <span className="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-accent-2 text-accent-ink">
                      <DynamicIcon name={t.icon} size={16} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-baseline justify-between">
                        <span className="truncate font-body text-[13px] text-ink">{t.name}</span>
                        <span className="ml-2 shrink-0 font-mono text-[11px] text-ink-3">{t.count}</span>
                      </div>
                      <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
                        <div className="h-full rounded-full bg-accent" style={{ width: `${maxCount ? (t.count / maxCount) * 100 : 0}%` }} />
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </AppShell>
  )
}

function Stat({ label, value, sub, primary }) {
  return (
    <div className={`rounded-qcard border p-3 text-center ${primary ? 'border-accent bg-accent-2' : 'border-line bg-surface'}`}>
      <p className="font-display text-[24px] font-extrabold leading-none text-ink">{value}</p>
      <p className="mt-1 font-body text-[12px] font-semibold text-ink-2">{label}</p>
      <p className="font-mono text-[9px] uppercase tracking-[0.08em] text-ink-3">{sub}</p>
    </div>
  )
}
