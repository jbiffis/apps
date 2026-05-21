import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useApi } from '../hooks/useApi.js'
import { getThemePref, resolveTheme, setThemePref } from '../theme.js'
import { clearSession, getUser } from '../auth.js'
import { flattenLeaves } from '../lib/catalog.js'
import { exportCsv, exportJson } from '../lib/export.js'
import AppShell from '../components/AppShell.jsx'
import BottomNav from '../components/BottomNav.jsx'
import { Sun, Logout, DynamicIcon } from '../icons/index.jsx'

const EMPTY = []

export default function Me() {
  const navigate = useNavigate()
  const user = getUser()
  const [pref, setPref] = useState(getThemePref())
  const [overrides, setOverrides] = useState(() => new Map()) // slug -> hidden (optimistic)
  const [busyExport, setBusyExport] = useState('')

  const catalog = useApi('/event-types?include=all')
  const prefs = useApi('/me/tracker-prefs')
  const leaves = useMemo(() => flattenLeaves(catalog.data || EMPTY), [catalog.data])
  const serverHidden = useMemo(
    () => new Set((prefs.data || EMPTY).filter((p) => p.hidden).map((p) => p.eventTypeSlug)),
    [prefs.data],
  )
  const isHidden = (slug) => (overrides.has(slug) ? overrides.get(slug) : serverHidden.has(slug))

  function cycleTheme() {
    const next = resolveTheme(pref) === 'dark' ? 'light' : 'dark'
    setThemePref(next)
    setPref(next)
  }
  function toggleHidden(slug) {
    const next = !isHidden(slug)
    setOverrides((prev) => new Map(prev).set(slug, next))
    api.put(`/me/tracker-prefs/${slug}`, { hidden: next }).catch(() => {
      // revert on failure
      setOverrides((prev) => new Map(prev).set(slug, !next))
    })
  }
  async function doExport(kind) {
    if (busyExport) return
    setBusyExport(kind)
    try {
      await (kind === 'csv' ? exportCsv() : exportJson())
    } catch { /* ignore */ } finally {
      setBusyExport('')
    }
  }
  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  const bar = (
    <header className="flex items-center justify-between px-[18px] pb-2.5 pt-3">
      <h1 className="font-display text-[22px] font-extrabold text-ink">Me</h1>
      <button onClick={cycleTheme} aria-label="Toggle theme"
        className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2">
        <Sun size={18} />
      </button>
    </header>
  )

  const onNav = (k) => { if (k !== 'me') navigate('/') }

  return (
    <AppShell bar={bar} nav={<BottomNav active="me" onSelect={onNav} />}>
      {/* Profile */}
      <section className="rounded-qcard border border-line bg-surface p-4">
        <div className="flex items-center gap-3">
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-accent text-white font-display text-[20px] font-extrabold">
            {(user?.displayName || user?.username || '?').slice(0, 1).toUpperCase()}
          </span>
          <div>
            <p className="font-display text-[18px] font-extrabold text-ink">{user?.displayName || user?.username}</p>
            <p className="font-mono text-[11px] text-ink-3">@{user?.username}{user?.gender ? ` · ${user.gender}` : ''}</p>
          </div>
        </div>
      </section>

      {/* Export */}
      <section className="mt-5 space-y-2">
        <h2 className="font-display text-[15px] font-bold text-ink">Export my data</h2>
        <div className="flex gap-2">
          <button onClick={() => doExport('csv')} disabled={!!busyExport}
            className="flex-1 rounded-[14px] border border-line bg-surface px-4 py-3 font-display text-[14px] font-bold text-ink disabled:opacity-50">
            {busyExport === 'csv' ? 'Exporting…' : 'Download CSV'}
          </button>
          <button onClick={() => doExport('json')} disabled={!!busyExport}
            className="flex-1 rounded-[14px] border border-line bg-surface px-4 py-3 font-display text-[14px] font-bold text-ink disabled:opacity-50">
            {busyExport === 'json' ? 'Exporting…' : 'Download JSON'}
          </button>
        </div>
      </section>

      {/* Manage trackers */}
      <section className="mt-5 space-y-2">
        <h2 className="font-display text-[15px] font-bold text-ink">Trackers</h2>
        <p className="font-body text-[11px] text-ink-3">Hidden trackers don’t show on Home or in the log picker.</p>
        {catalog.loading ? (
          <p className="font-body text-[13px] text-ink-3">Loading…</p>
        ) : (
          <ul className="space-y-2">
            {leaves.map((l) => {
              const hidden = isHidden(l.slug)
              return (
                <li key={l.slug} className="flex items-center gap-3 rounded-2xl border border-line bg-surface px-3 py-2.5">
                  <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-xl ${hidden ? 'bg-surface-2 text-ink-3' : 'bg-accent-2 text-accent-ink'}`}>
                    <DynamicIcon name={l.icon} size={18} />
                  </span>
                  <span className={`flex-1 truncate font-body text-[13px] ${hidden ? 'text-ink-3 line-through' : 'text-ink'}`}>{l.name}</span>
                  <button
                    onClick={() => toggleHidden(l.slug)}
                    aria-pressed={!hidden}
                    className={`shrink-0 rounded-full px-3 py-1.5 font-body text-[12px] ${hidden ? 'border border-line bg-surface-2 text-ink-2' : 'bg-accent text-white'}`}
                  >
                    {hidden ? 'Hidden' : 'Shown'}
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <button onClick={logout}
        className="mt-6 flex w-full items-center justify-center gap-2 rounded-[14px] border border-line bg-surface px-4 py-3 font-display text-[14px] font-bold text-warn">
        <Logout size={16} /> Sign out
      </button>
    </AppShell>
  )
}
