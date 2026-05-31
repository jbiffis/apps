import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useApi } from '../hooks/useApi.js'
import { flattenLeaves } from '../lib/catalog.js'
import { dimensionIndex, optionFormatter } from '../lib/units.js'
import { dayKey, dayHeading, timeLabel, summarizeOptions } from '../lib/format.js'
import { Back, Close, DynamicIcon } from '../icons/index.jsx'

const WINDOW_DAYS = 30
const PAGE = 100

export default function History() {
  const navigate = useNavigate()
  const [selected, setSelected] = useState(null)
  const [events, setEvents] = useState([])
  const [cursor, setCursor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  // Fix the query window once at mount (lazy initializer) so the base path is
  // stable; pages are appended via the keyset cursor.
  const [basePath] = useState(() => {
    const to = new Date()
    const from = new Date(Date.now() - WINDOW_DAYS * 86400000)
    return `/logged-events?from=${from.toISOString()}&to=${to.toISOString()}&limit=${PAGE}`
  })

  useEffect(() => {
    let alive = true
    api.get(basePath)
      .then((r) => { if (!alive) return; setEvents(r.events || []); setCursor(r.nextCursor || null) })
      .catch(() => { /* surfaced as empty state */ })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [basePath])

  async function loadMore() {
    if (!cursor || loadingMore) return
    setLoadingMore(true)
    try {
      const r = await api.get(`${basePath}&cursor=${encodeURIComponent(cursor)}`)
      setEvents((prev) => [...prev, ...(r.events || [])])
      setCursor(r.nextCursor || null)
    } catch {
      /* keep cursor so the user can retry */
    } finally {
      setLoadingMore(false)
    }
  }

  const catalog = useApi('/event-types')
  const unitPrefs = useApi('/me/preferences')
  const iconBySlug = useMemo(() => {
    const m = {}
    flattenLeaves(catalog.data || []).forEach((l) => { m[l.slug] = l.icon })
    return m
  }, [catalog.data])
  // Convert weight/height/temperature option values to the user's units.
  const formatOption = useMemo(
    () => optionFormatter(dimensionIndex(catalog.data || []), unitPrefs.data),
    [catalog.data, unitPrefs.data],
  )

  // Group newest-first events into day buckets, preserving order.
  const groups = useMemo(() => {
    const out = []
    let cur = null
    for (const e of events) {
      const k = dayKey(e.occurredAt)
      if (!cur || cur.key !== k) {
        cur = { key: k, heading: dayHeading(e.occurredAt), items: [] }
        out.push(cur)
      }
      cur.items.push(e)
    }
    return out
  }, [events])

  return (
    <div className="mx-auto flex min-h-full max-w-[480px] flex-col bg-bg">
      <header className="flex items-center gap-3 px-[18px] pb-2.5 pt-3">
        <button onClick={() => navigate(-1)} aria-label="Back"
          className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2">
          <Back size={18} />
        </button>
        <h1 className="font-display text-[22px] font-extrabold text-ink">History</h1>
      </header>

      <main className="flex-1 overflow-y-auto px-[18px] pb-10">
        {loading ? (
          <p className="font-body text-[13px] text-ink-3">Loading…</p>
        ) : events.length === 0 ? (
          <p className="font-body text-[13px] text-ink-3">No entries in the last {WINDOW_DAYS} days.</p>
        ) : (
          <>
            {groups.map((g) => (
              <section key={g.key} className="mb-5">
                <h2 className="mb-2 font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">{g.heading}</h2>
                <ul className="space-y-2">
                  {g.items.map((e) => {
                    const sub = summarizeOptions(e.options, (o) => formatOption(e.eventType?.slug, o)) || e.note
                    return (
                      <li key={e.id}>
                        <button onClick={() => setSelected(e)}
                          className="flex w-full items-center gap-3 rounded-2xl border border-line bg-surface px-3 py-2.5 text-left">
                          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-accent-2 text-accent-ink">
                            <DynamicIcon name={iconBySlug[e.eventType?.slug]} size={18} />
                          </span>
                          <div className="min-w-0 flex-1">
                            <p className="truncate font-body text-[13px] font-semibold text-ink">{e.eventType?.name || e.eventType?.slug}</p>
                            {sub && <p className="truncate font-body text-[11px] text-ink-3">{sub}</p>}
                          </div>
                          <span className="shrink-0 font-mono text-[10px] text-ink-3">{timeLabel(e.occurredAt)}</span>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </section>
            ))}
            {cursor ? (
              <button
                onClick={loadMore}
                disabled={loadingMore}
                className="mx-auto mb-4 block rounded-full border border-line bg-surface px-5 py-2 font-display text-[13px] font-bold text-ink disabled:opacity-50"
              >
                {loadingMore ? 'Loading…' : 'Load more'}
              </button>
            ) : (
              <p className="pb-4 text-center font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">
                End of the last {WINDOW_DAYS} days
              </p>
            )}
          </>
        )}
      </main>

      {selected && (
        <EntryDetail
          entry={selected}
          icon={iconBySlug[selected.eventType?.slug]}
          formatValue={(o) => formatOption(selected.eventType?.slug, o)}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  )
}

function EntryDetail({ entry, icon, formatValue, onClose }) {
  const d = new Date(entry.occurredAt)
  return (
    <div className="fixed inset-0 z-30 mx-auto max-w-[480px]" role="dialog" aria-modal="true" aria-label="Entry detail">
      <button aria-label="Close" onClick={onClose} className="absolute inset-0 h-full w-full bg-black/40" />
      <div className="absolute inset-x-0 bottom-0 max-h-[80vh] overflow-y-auto rounded-t-3xl border-t border-line bg-surface p-4 pb-8">
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-line" />
        <div className="mb-4 flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-accent-2 text-accent-ink">
            <DynamicIcon name={icon} size={22} />
          </span>
          <div className="flex-1">
            <h2 className="font-display text-[20px] font-extrabold text-ink">{entry.eventType?.name || entry.eventType?.slug}</h2>
            <p className="font-mono text-[11px] text-ink-3">
              {dayHeading(entry.occurredAt)} · {d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}
            </p>
          </div>
          <button onClick={onClose} aria-label="Close"
            className="grid h-8 w-8 place-items-center rounded-full border border-line bg-bg text-ink-2">
            <Close size={16} />
          </button>
        </div>

        {(entry.options || []).length > 0 && (
          <dl className="mb-3 space-y-2">
            {entry.options.map((o, i) => (
              <div key={i} className="flex items-start justify-between gap-3 rounded-xl border border-line bg-bg px-3 py-2">
                <dt className="font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">{o.property}</dt>
                <dd className="text-right font-body text-[13px] font-semibold text-ink">
                  {formatValue(o)}
                </dd>
              </div>
            ))}
          </dl>
        )}

        {entry.note && (
          <div className="rounded-xl border border-line bg-bg px-3 py-2">
            <p className="mb-1 font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">Note</p>
            <p className="font-body text-[13px] text-ink">{entry.note}</p>
          </div>
        )}
      </div>
    </div>
  )
}
