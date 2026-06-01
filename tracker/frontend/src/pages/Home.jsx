import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { getThemePref, resolveTheme, setThemePref } from '../theme.js'
import { clearSession, getUser } from '../auth.js'
import { api } from '../api.js'
import { useApi } from '../hooks/useApi.js'
import { useLongPress } from '../hooks/useLongPress.js'
import { flattenLeaves } from '../lib/catalog.js'
import { dimensionIndex, optionFormatter } from '../lib/units.js'
import { greeting, dateLabel, timeLabel, summarizeOptions } from '../lib/format.js'
import AppShell from '../components/AppShell.jsx'
import BottomNav from '../components/BottomNav.jsx'
import TrackerPickerSheet from '../components/TrackerPickerSheet.jsx'
import ActionSheet from '../components/ActionSheet.jsx'
import Toast from '../components/Toast.jsx'
import { Sun, Logout, Plus, Close, DynamicIcon } from '../icons/index.jsx'

const EMPTY = []

export default function Home() {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getUser()
  const [pref, setPref] = useState(getThemePref())
  const [sheet, setSheet] = useState(null)
  const [menuEntry, setMenuEntry] = useState(null)
  const [notice, setNotice] = useState(null)
  const [order, setOrder] = useState(null) // slug[] of visible tiles while in edit mode
  const [hiddenEdits, setHiddenEdits] = useState(() => new Map()) // slug -> hidden, optimistic during edit

  const hero = useApi('/home/hero')
  const today = useApi('/home/today')
  const catalog = useApi('/event-types')
  const prefs = useApi('/me/tracker-prefs')
  const unitPrefs = useApi('/me/preferences')

  // A just-saved entry hands off via router state; derive the undo toast from
  // it (no extra state) and refresh the feeds once it appears.
  const saved = location.state?.saved
  useEffect(() => {
    if (saved) { hero.reload(); today.reload() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [saved?.id])

  const clearSaved = () => navigate('.', { replace: true, state: {} })

  async function undoSave() {
    const id = saved?.id
    clearSaved()
    if (!id) return
    try {
      await api.del(`/logged-events/${id}`)
      hero.reload()
      today.reload()
    } catch { /* already gone / window passed — nothing to do */ }
  }
  const tree = catalog.data || EMPTY
  // Trackers the user hid in the Me tab — dropped from the grid + picker.
  const hidden = useMemo(
    () => new Set((prefs.data || EMPTY).filter((p) => p.hidden).map((p) => p.eventTypeSlug)),
    [prefs.data],
  )
  const visibleTree = useMemo(
    () => tree.filter((n) => n.isCategory || !hidden.has(n.slug)),
    [tree, hidden],
  )
  // Grid order: saved per-user sort_order first (ascending), then catalog order
  // (Array.sort is stable, so unset entries keep their original position).
  const orderedTree = useMemo(() => {
    const so = new Map(
      (prefs.data || EMPTY).filter((p) => p.sortOrder != null).map((p) => [p.eventTypeSlug, p.sortOrder]),
    )
    return [...visibleTree].sort(
      (a, b) => (so.has(a.slug) ? so.get(a.slug) : Infinity) - (so.has(b.slug) ? so.get(b.slug) : Infinity),
    )
  }, [visibleTree, prefs.data])
  // Edit mode renders both visible and just-hidden tiles, so look up over the
  // full top-level tree (visibleTree drops hidden ones).
  const topBySlug = useMemo(() => {
    const m = {}
    for (const n of tree) m[n.slug] = n
    return m
  }, [tree])
  const reordering = order != null
  // Effective hidden state during edit mode (optimistic edits over server prefs).
  const effHidden = (slug) => (hiddenEdits.has(slug) ? hiddenEdits.get(slug) : hidden.has(slug))
  // Top-level loggable tiles the user has hidden — shown in the edit-mode tray.
  const hiddenTiles = useMemo(
    () => tree.filter((n) => !n.isCategory && effHidden(n.slug)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [tree, hidden, hiddenEdits],
  )
  // The today/logged-event payload's eventType carries only {slug, name};
  // resolve its icon from the catalog we already fetched.
  const iconBySlug = useMemo(() => {
    const m = {}
    flattenLeaves(tree).forEach((l) => { m[l.slug] = l.icon })
    return m
  }, [tree])
  // Convert weight/height/temperature option values to the user's units.
  const formatOption = useMemo(
    () => optionFormatter(dimensionIndex(tree), unitPrefs.data),
    [tree, unitPrefs.data],
  )

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
    setSheet(null)
    navigate(`/log/${slug}`)
  }
  function openTile(node) {
    // Categories open the picker rooted at their children (sub-categories like
    // Eyes drill down further); leaves go straight to logging.
    if (node.isCategory) setSheet({ key: node.slug, title: node.name, nodes: node.children || [], slug: node.slug })
    else logTracker(node.slug)
  }
  function openFab() {
    setSheet({ key: 'all', title: 'Log something', nodes: visibleTree, slug: '' })
  }
  function newThing(parentSlug) {
    setSheet(null)
    navigate(parentSlug ? `/new?parent=${encodeURIComponent(parentSlug)}` : '/new')
  }

  function enterEdit() {
    setHiddenEdits(new Map())
    setOrder(orderedTree.map((n) => n.slug))
  }
  function moveTile(i, dir) {
    const j = i + dir
    if (j < 0 || j >= order.length) return
    setOrder((prev) => {
      const next = [...prev]
      ;[next[i], next[j]] = [next[j], next[i]]
      return next
    })
  }
  // Hide a tile from the grid (still creatable/loggable via the Me tab) —
  // optimistic, drops it from the working order and the persisted prefs.
  function hideTile(slug) {
    setHiddenEdits((prev) => new Map(prev).set(slug, true))
    setOrder((prev) => prev.filter((s) => s !== slug))
    api.put(`/me/tracker-prefs/${slug}`, { hidden: true }).catch(() => {
      setHiddenEdits((prev) => new Map(prev).set(slug, false))
      setOrder((prev) => (prev.includes(slug) ? prev : [...prev, slug]))
    })
  }
  function showTile(slug) {
    setHiddenEdits((prev) => new Map(prev).set(slug, false))
    setOrder((prev) => (prev.includes(slug) ? prev : [...prev, slug]))
    api.put(`/me/tracker-prefs/${slug}`, { hidden: false }).catch(() => {
      setHiddenEdits((prev) => new Map(prev).set(slug, true))
      setOrder((prev) => prev.filter((s) => s !== slug))
    })
  }
  async function saveOrder() {
    const slugs = order
    setOrder(null)
    setHiddenEdits(new Map())
    try {
      await Promise.all(slugs.map((slug, i) => api.put(`/me/tracker-prefs/${slug}`, { sortOrder: i })))
      prefs.reload()
    } catch { /* leave as-is; reload reflects server */ }
  }

  function editEntry(e) {
    navigate(`/log/${e.eventType.slug}?edit=${e.id}`)
  }

  async function deleteEntry(e) {
    try {
      await api.del(`/logged-events/${e.id}`)
      hero.reload()
      today.reload()
      setNotice({ message: `Deleted ${e.eventType?.name || e.eventType?.slug}`, restore: e })
    } catch { /* already gone */ }
  }

  // Undo a delete by restoring the same (soft-deleted) row — preserves its id.
  async function undoDelete() {
    const e = notice?.restore
    setNotice(null)
    if (!e) return
    try {
      await api.post(`/logged-events/${e.id}/restore`)
      hero.reload()
      today.reload()
    } catch { /* nothing to do */ }
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
    <AppShell bar={bar} nav={<BottomNav active="home" onSelect={(k) => { if (k === 'log') openFab(); else if (k === 'me') navigate('/me'); else if (k === 'stats') navigate('/stats') }} />}>
      {/* Hero cards */}
      <section className="grid grid-cols-3 gap-2">
        {hero.loading && <CardSkeleton />}
        {hero.data?.map((c, i) => (
          <HeroCard key={c.eventTypeSlug} card={c} primary={c.primary ?? i === 0} onClick={() => logTracker(c.eventTypeSlug)} />
        ))}
      </section>

      {/* All trackers */}
      <section className="mt-6 space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-[15px] font-bold text-ink">All trackers</h2>
          {reordering && (
            <button onClick={saveOrder} className="font-mono text-[11px] text-accent">Done</button>
          )}
        </div>
        {catalog.loading ? (
          <p className="font-body text-[13px] text-ink-3">Loading…</p>
        ) : reordering ? (
          <>
            <p className="font-body text-[11px] text-ink-3">Use ◀ ▶ to reorder, ✕ to hide, then tap Done.</p>
            <div className="grid grid-cols-4 gap-3">
              {order.map((slug, i) => {
                const node = topBySlug[slug]
                if (!node) return null
                return (
                  <div key={slug} className="flex flex-col items-center gap-1">
                    <span className="relative grid h-[54px] w-[54px] place-items-center rounded-2xl border-2 border-accent bg-surface text-ink-2">
                      <DynamicIcon name={node.icon} size={24} />
                      <button onClick={() => hideTile(slug)} aria-label={`Hide ${node.name}`}
                        className="absolute -right-1.5 -top-1.5 grid h-5 w-5 place-items-center rounded-full bg-warn text-white shadow">
                        <Close size={12} />
                      </button>
                    </span>
                    <div className="flex items-center gap-1">
                      <button onClick={() => moveTile(i, -1)} disabled={i === 0} aria-label="Move left"
                        className="font-mono text-[12px] text-accent disabled:opacity-30">◀</button>
                      <button onClick={() => moveTile(i, 1)} disabled={i === order.length - 1} aria-label="Move right"
                        className="font-mono text-[12px] text-accent disabled:opacity-30">▶</button>
                    </div>
                  </div>
                )
              })}
            </div>
            {hiddenTiles.length > 0 && (
              <div className="mt-4 space-y-2 rounded-2xl border border-line bg-surface-2 p-3">
                <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">Hidden ({hiddenTiles.length})</p>
                <div className="grid grid-cols-4 gap-3">
                  {hiddenTiles.map((node) => (
                    <button key={node.slug} onClick={() => showTile(node.slug)} aria-label={`Restore ${node.name}`}
                      className="flex flex-col items-center gap-1">
                      <span className="grid h-[54px] w-[54px] place-items-center rounded-2xl border border-dashed border-line bg-bg text-ink-3 opacity-60">
                        <DynamicIcon name={node.icon} size={24} />
                      </span>
                      <span className="w-full truncate text-center font-body text-[11px] text-ink-3">{node.name}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="grid grid-cols-4 gap-3">
            {orderedTree.map((node) => (
              <TrackerTile key={node.slug} node={node} onOpen={() => openTile(node)} onLongPress={enterEdit} />
            ))}
            <button onClick={() => newThing('')} className="flex select-none flex-col items-center gap-1.5">
              <span className="grid h-[54px] w-[54px] place-items-center rounded-2xl border border-dashed border-accent bg-surface text-accent">
                <Plus size={24} />
              </span>
              <span className="w-full truncate text-center font-body text-[11px] text-accent">New</span>
            </button>
          </div>
        )}
      </section>

      {/* Today */}
      <section className="mt-6 space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-[15px] font-bold text-ink">Today</h2>
          <button onClick={() => navigate('/history')} className="font-mono text-[11px] text-accent">
            See all →
          </button>
        </div>
        {today.loading ? (
          <p className="font-body text-[13px] text-ink-3">Loading…</p>
        ) : today.data?.length ? (
          <ul className="space-y-2">
            {today.data.map((e) => (
              <TodayRow key={e.id} entry={e} icon={iconBySlug[e.eventType?.slug]} formatOption={formatOption} onMenu={() => setMenuEntry(e)} />
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

      {sheet && (
        <TrackerPickerSheet
          key={sheet.key}
          rootTitle={sheet.title}
          rootNodes={sheet.nodes}
          rootSlug={sheet.slug}
          hidden={hidden}
          onPick={logTracker}
          onNew={newThing}
          onClose={() => setSheet(null)}
        />
      )}

      {menuEntry && (
        <ActionSheet
          title={menuEntry.eventType?.name || menuEntry.eventType?.slug}
          actions={[
            { label: 'Edit', onSelect: () => editEntry(menuEntry) },
            { label: 'Delete', danger: true, onSelect: () => deleteEntry(menuEntry) },
          ]}
          onClose={() => setMenuEntry(null)}
        />
      )}

      {notice ? (
        <Toast message={notice.message} onUndo={undoDelete} onDismiss={() => setNotice(null)} />
      ) : saved ? (
        <Toast message={`${saved.verb || 'Logged'} ${saved.name}`} onUndo={saved.undoable === false ? undefined : undoSave} onDismiss={clearSaved} />
      ) : null}
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

function TrackerTile({ node, onOpen, onLongPress }) {
  const lp = useLongPress(onLongPress)
  return (
    <button {...lp} onClick={onOpen} className="flex select-none flex-col items-center gap-1.5">
      <span className="grid h-[54px] w-[54px] place-items-center rounded-2xl border border-line bg-surface text-ink-2">
        <DynamicIcon name={node.icon} size={24} />
      </span>
      <span className="w-full truncate text-center font-body text-[11px] text-ink-3">{node.name}</span>
    </button>
  )
}

function TodayRow({ entry, icon, formatOption, onMenu }) {
  const lp = useLongPress(onMenu)
  const sub = summarizeOptions(entry.options, (o) => formatOption(entry.eventType?.slug, o)) || entry.note
  return (
    <li
      {...lp}
      className="flex select-none items-center gap-3 rounded-2xl border border-line bg-surface px-3 py-2.5"
    >
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-accent-2 text-accent-ink">
        <DynamicIcon name={icon} size={18} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-body text-[13px] font-semibold text-ink">{entry.eventType?.name || entry.eventType?.slug}</p>
        {sub && <p className="truncate font-body text-[11px] text-ink-3">{sub}</p>}
      </div>
      <span className="shrink-0 font-mono text-[10px] text-ink-3">{timeLabel(entry.occurredAt)}</span>
    </li>
  )
}
