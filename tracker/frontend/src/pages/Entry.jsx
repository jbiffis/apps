import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api.js'
import { useApi } from '../hooks/useApi.js'
import FieldRenderer from '../components/FieldRenderer.jsx'
import TimeChips from '../components/TimeChips.jsx'
import { Back, DynamicIcon } from '../icons/index.jsx'

// Initial value for a property so steppers/toggles start sensibly; selects and
// text start empty (and are validated if required).
function initialValue(p) {
  const w = p.preset?.widget
  const o = p.preset?.options || {}
  if (w === 'number' || w === 'dose' || w === 'duration') {
    return typeof o.default === 'number' ? o.default : (typeof o.min === 'number' ? o.min : 0)
  }
  if (w === 'bool') return false
  if (w === 'multi_select') return []
  return undefined
}

function hasValue(p, v) {
  const w = p.preset?.widget
  if (w === 'multi_select') return Array.isArray(v) && v.length > 0
  if (w === 'text') return typeof v === 'string' && v.trim() !== ''
  if (w === 'bool' || w === 'number' || w === 'dose' || w === 'duration') return v !== undefined && v !== null
  return v !== undefined && v !== null && v !== ''
}

export default function Entry() {
  const { slug } = useParams()
  const navigate = useNavigate()
  const { data: type, loading, error } = useApi(`/event-types/${slug}`)

  const properties = useMemo(() => type?.properties || [], [type])
  // `values` holds only user edits (keyed by property name); unedited fields
  // fall back to their widget default via valueFor(). Avoids seeding state in
  // an effect. "Save another" resets simply by clearing this back to {}.
  const [values, setValues] = useState({})
  const [when, setWhen] = useState(() => new Date())
  const [note, setNote] = useState('')
  const [missing, setMissing] = useState([])
  const [submitError, setSubmitError] = useState('')
  const [busy, setBusy] = useState(false)
  const [flash, setFlash] = useState(false)

  const valueFor = (p) => (p.name in values ? values[p.name] : initialValue(p))
  const setValue = (name, v) => setValues((prev) => ({ ...prev, [name]: v }))

  function buildOptions() {
    const out = []
    for (const p of properties) {
      const v = valueFor(p)
      if (hasValue(p, v)) out.push({ propertyName: p.name, value: v })
    }
    return out
  }

  async function save({ another } = {}) {
    if (busy) return
    setSubmitError('')
    const miss = properties.filter((p) => p.required && !hasValue(p, valueFor(p))).map((p) => p.name)
    if (miss.length) {
      setMissing(miss)
      return
    }
    setMissing([])
    setBusy(true)
    try {
      const created = await api.post('/logged-events', {
        eventTypeSlug: slug,
        occurredAt: (when || new Date()).toISOString(),
        note: note.trim() || undefined,
        options: buildOptions(),
      })
      if (another) {
        // stay on screen, reset to defaults, brief confirmation
        setValues({})
        setNote('')
        setWhen(new Date())
        setFlash(true)
        setTimeout(() => setFlash(false), 1500)
      } else {
        navigate('/', { replace: true, state: { saved: { id: created.id, name: type?.name || 'Entry' } } })
      }
    } catch (e) {
      setSubmitError(e instanceof ApiError ? (e.message || 'Could not save.') : 'Could not save.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-[480px] flex-col bg-bg">
      <header className="flex items-center gap-3 px-[18px] pb-2.5 pt-3">
        <button onClick={() => navigate(-1)} aria-label="Back"
          className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2">
          <Back size={18} />
        </button>
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-accent-2 text-accent-ink">
          <DynamicIcon name={type?.icon} size={18} />
        </span>
        <h1 className="font-display text-[22px] font-extrabold text-ink">{type?.name || 'Log'}</h1>
      </header>

      <main className="flex-1 space-y-3 overflow-y-auto px-[18px] pb-40">
        {loading && <p className="font-body text-[13px] text-ink-3">Loading…</p>}
        {error && <p className="font-body text-[13px] text-warn">Couldn’t load this tracker.</p>}

        {type && (
          <>
            {properties.map((p) => (
              <div key={p.id}>
                <FieldRenderer property={p} value={valueFor(p)} onChange={(v) => setValue(p.name, v)} />
                {missing.includes(p.name) && (
                  <p className="mt-1 px-1 font-body text-[11px] text-warn">{p.name} is required.</p>
                )}
              </div>
            ))}

            <TimeChips value={when} onChange={setWhen} />

            <div className="rounded-field border border-line bg-surface px-4 py-3">
              <span className="mb-2 block font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">Note</span>
              <textarea
                value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={280}
                placeholder="Optional…"
                className="w-full resize-none bg-transparent font-body text-[14px] text-ink outline-none placeholder:text-ink-3"
              />
            </div>

            {submitError && <p role="alert" className="px-1 font-body text-[13px] text-warn">{submitError}</p>}
          </>
        )}
      </main>

      {/* Sticky save bar */}
      {type && (
        <div className="sticky bottom-0 flex items-center gap-3 border-t border-line bg-bg px-[18px] py-3">
          <button
            type="button" onClick={() => save({ another: true })} disabled={busy}
            className="rounded-[14px] border border-line bg-surface px-4 py-3 font-display text-[14px] font-bold text-ink disabled:opacity-50">
            {flash ? 'Saved ✓' : 'Save another'}
          </button>
          <button
            type="button" onClick={() => save()} disabled={busy}
            className="flex-1 rounded-[14px] px-4 py-3 font-display text-[15px] font-bold text-white shadow-md disabled:opacity-50"
            style={{ background: 'linear-gradient(160deg, var(--accent), var(--accent-deep))' }}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      )}
    </div>
  )
}
