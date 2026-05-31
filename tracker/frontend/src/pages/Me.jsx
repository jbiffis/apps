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

  const onNav = (k) => { if (k === 'stats') navigate('/stats'); else if (k !== 'me') navigate('/') }

  return (
    <AppShell bar={bar} nav={<BottomNav active="me" onSelect={onNav} />}>
      {/* Profile */}
      <section className="rounded-qcard border border-line bg-surface p-4">
        <div className="flex items-center gap-3">
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-accent text-white font-display text-[20px] font-extrabold">
            {(user?.displayName || user?.email || '?').slice(0, 1).toUpperCase()}
          </span>
          <div>
            <p className="font-display text-[18px] font-extrabold text-ink">{user?.displayName || user?.email}</p>
            <p className="font-mono text-[11px] text-ink-3">{user?.email}{user?.gender ? ` · ${user.gender}` : ''}</p>
          </div>
        </div>
      </section>

      {/* Biometrics */}
      <BiometricsSection />

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

const SEX_OPTS = [['', '—'], ['male', 'Male'], ['female', 'Female'], ['intersex', 'Intersex']]
const BLOOD_OPTS = [['', '—'], ['A+', 'A+'], ['A-', 'A−'], ['B+', 'B+'], ['B-', 'B−'],
  ['AB+', 'AB+'], ['AB-', 'AB−'], ['O+', 'O+'], ['O-', 'O−']]
const ACTIVITY_OPTS = [['', '—'], ['sedentary', 'Sedentary'], ['light', 'Light'],
  ['moderate', 'Moderate'], ['active', 'Active'], ['very_active', 'Very active']]
const GOAL_OPTS = [['', '—'], ['lose', 'Lose'], ['maintain', 'Maintain'], ['gain', 'Gain']]

const BLANK_FORM = {
  dateOfBirth: '', biologicalSex: '', bloodType: '',
  activityLevel: '', weightGoal: '', drugAllergies: '', chronicConditions: '',
}

// Pull only the editable (stored) fields out of the API view; derived fields
// (age, weight, height, bmi) are read-only and never go into the form.
function toForm(d) {
  if (!d) return BLANK_FORM
  return {
    dateOfBirth: d.dateOfBirth ?? '',
    biologicalSex: d.biologicalSex ?? '',
    bloodType: d.bloodType ?? '',
    activityLevel: d.activityLevel ?? '',
    weightGoal: d.weightGoal ?? '',
    drugAllergies: d.drugAllergies ?? '',
    chronicConditions: d.chronicConditions ?? '',
  }
}

/**
 * Biometric profile. Stored facts are editable; weight/height/age/BMI are
 * derived server-side (weight & height come from the most recent log, so they
 * update by logging the Weight / Height trackers, not here).
 */
function BiometricsSection() {
  const { data, loading, reload } = useApi('/me/biometrics')
  const [form, setForm] = useState(BLANK_FORM)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  // Sync the form from the server view when it (re)loads — render-phase rather
  // than in an effect, per the React "adjusting state on data change" pattern.
  const [syncedFrom, setSyncedFrom] = useState(null)
  if (data && data !== syncedFrom) {
    setSyncedFrom(data)
    setForm(toForm(data))
  }

  const set = (k) => (e) => {
    setForm((f) => ({ ...f, [k]: e.target.value }))
    setSaved(false)
  }

  async function save() {
    if (saving) return
    setSaving(true)
    try {
      await api.put('/me/biometrics', { ...form, dateOfBirth: form.dateOfBirth || null })
      await reload()
      setSaved(true)
    } catch { /* surfaced by the 401 handler / left silent for field errors */ } finally {
      setSaving(false)
    }
  }

  const stat = (label, value) => (
    <div className="rounded-2xl border border-line bg-surface px-3 py-2.5">
      <p className="font-mono text-[10px] uppercase tracking-wide text-ink-3">{label}</p>
      <p className="font-display text-[18px] font-extrabold text-ink">{value}</p>
    </div>
  )

  return (
    <section className="mt-5 space-y-3">
      <h2 className="font-display text-[15px] font-bold text-ink">Biometrics</h2>
      <p className="font-body text-[11px] text-ink-3">
        Weight &amp; height come from your most recent log — update them by logging the Weight or Height trackers.
      </p>

      {/* Derived (read-only) */}
      <div className="grid grid-cols-2 gap-2">
        {stat('Age', data?.age != null ? `${data.age}` : '—')}
        {stat('BMI', data?.bmi != null ? `${data.bmi}` : '—')}
        {stat('Weight', data?.latestWeightKg != null ? `${data.latestWeightKg} kg` : '—')}
        {stat('Height', data?.latestHeightCm != null ? `${data.latestHeightCm} cm` : '—')}
      </div>

      {/* Stored (editable) */}
      <div className="space-y-3 rounded-qcard border border-line bg-surface p-4">
        <Field label="Date of birth">
          <input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')}
            className="w-full rounded-[14px] border border-line bg-surface px-3 py-2.5 font-body text-[14px] text-ink" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Biological sex">
            <Select value={form.biologicalSex} onChange={set('biologicalSex')} opts={SEX_OPTS} />
          </Field>
          <Field label="Blood type">
            <Select value={form.bloodType} onChange={set('bloodType')} opts={BLOOD_OPTS} />
          </Field>
          <Field label="Activity level">
            <Select value={form.activityLevel} onChange={set('activityLevel')} opts={ACTIVITY_OPTS} />
          </Field>
          <Field label="Weight goal">
            <Select value={form.weightGoal} onChange={set('weightGoal')} opts={GOAL_OPTS} />
          </Field>
        </div>
        <Field label="Drug allergies">
          <textarea value={form.drugAllergies} onChange={set('drugAllergies')} rows={2}
            placeholder="e.g. penicillin"
            className="w-full resize-none rounded-[14px] border border-line bg-surface px-3 py-2.5 font-body text-[14px] text-ink" />
        </Field>
        <Field label="Chronic conditions">
          <textarea value={form.chronicConditions} onChange={set('chronicConditions')} rows={2}
            placeholder="e.g. asthma"
            className="w-full resize-none rounded-[14px] border border-line bg-surface px-3 py-2.5 font-body text-[14px] text-ink" />
        </Field>
        <button onClick={save} disabled={saving || loading}
          className="w-full rounded-[14px] bg-accent px-4 py-3 font-display text-[14px] font-bold text-white disabled:opacity-50">
          {saving ? 'Saving…' : saved ? 'Saved ✓' : 'Save biometrics'}
        </button>
      </div>
    </section>
  )
}

function Field({ label, children }) {
  return (
    <label className="block space-y-1">
      <span className="font-body text-[11px] text-ink-3">{label}</span>
      {children}
    </label>
  )
}

function Select({ value, onChange, opts }) {
  return (
    <select value={value} onChange={onChange}
      className="w-full rounded-[14px] border border-line bg-surface px-3 py-2.5 font-body text-[14px] text-ink">
      {opts.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
    </select>
  )
}
