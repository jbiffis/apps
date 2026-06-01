import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api.js'
import { useApi } from '../hooks/useApi.js'
import { flattenTree } from '../lib/catalog.js'
import { Back, Close, DynamicIcon, pickableIconNames } from '../icons/index.jsx'

// Full-screen "new tracker / new category" form. Everything created here is
// shared with all users (per-user ownership is a later phase); a user hides
// what they don't want from the Me tab. Reached via /new?parent=<slug> — the
// parent is prefilled from wherever the "+ New" tile was tapped (none at root).
export default function CreateTracker() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const parentSlug = params.get('parent') || ''

  const catalog = useApi('/event-types?include=all')
  const presets = useApi('/property-presets')

  const parent = useMemo(() => {
    if (!parentSlug) return null
    return flattenTree(catalog.data || []).find((n) => n.slug === parentSlug) || null
  }, [catalog.data, parentSlug])

  const [isCategory, setIsCategory] = useState(false)
  const [name, setName] = useState('')
  const [icon, setIcon] = useState('Sparkle')
  const [fields, setFields] = useState([]) // { presetSlug, name, required }
  const [picking, setPicking] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  function addField(preset) {
    setPicking(false)
    setFields((prev) => [...prev, { presetSlug: preset.slug, name: preset.name, required: false }])
  }
  function patchField(i, patch) {
    setFields((prev) => prev.map((f, j) => (j === i ? { ...f, ...patch } : f)))
  }
  function removeField(i) {
    setFields((prev) => prev.filter((_, j) => j !== i))
  }
  function moveField(i, dir) {
    const j = i + dir
    setFields((prev) => {
      if (j < 0 || j >= prev.length) return prev
      const next = [...prev]
      ;[next[i], next[j]] = [next[j], next[i]]
      return next
    })
  }

  async function submit() {
    if (busy) return
    setError('')
    if (!name.trim()) { setError('Give it a name.'); return }
    if (!isCategory && fields.some((f) => !f.name.trim())) { setError('Every field needs a name.'); return }
    setBusy(true)
    const body = {
      parentSlug: parentSlug || null,
      name: name.trim(),
      icon,
      isCategory,
      properties: isCategory ? [] : fields.map((f, i) => ({
        name: f.name.trim(),
        presetSlug: f.presetSlug,
        required: f.required,
        sortOrder: i,
      })),
    }
    try {
      await api.post('/event-types', body)
      navigate('/', { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setError(`A tracker named "${name.trim()}" already exists. Pick another name.`)
      } else {
        setError(e instanceof ApiError ? (e.message || 'Could not create.') : 'Could not create.')
      }
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
        <h1 className="font-display text-[22px] font-extrabold text-ink">New</h1>
      </header>

      <main className="flex-1 space-y-4 overflow-y-auto px-[18px] pb-40">
        {parent && (
          <p className="font-body text-[12px] text-ink-3">
            Inside <span className="font-semibold text-ink-2">{parent.name}</span>
          </p>
        )}

        {/* Type toggle */}
        <div className="flex gap-2">
          {[['Tracker', false], ['Category', true]].map(([label, cat]) => (
            <button key={label} type="button" onClick={() => setIsCategory(cat)}
              className={`flex-1 rounded-[14px] px-4 py-2.5 font-display text-[14px] font-bold ${
                isCategory === cat ? 'bg-accent text-white' : 'border border-line bg-surface text-ink-2'
              }`}>
              {label}
            </button>
          ))}
        </div>
        <p className="font-body text-[11px] text-ink-3">
          {isCategory
            ? 'A category groups other trackers. It has no fields of its own.'
            : 'A tracker is something you log. Add the fields you want to record.'}
        </p>

        {/* Name */}
        <div className="rounded-field border border-line bg-surface px-4 py-3">
          <span className="mb-2 block font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">Name</span>
          <input
            value={name} onChange={(e) => setName(e.target.value)} maxLength={60}
            placeholder={isCategory ? 'e.g. Fitness' : 'e.g. Vitamin D'}
            className="w-full bg-transparent font-display text-[18px] font-bold text-ink outline-none placeholder:text-ink-3"
          />
        </div>

        {/* Icon */}
        <div className="rounded-field border border-line bg-surface px-4 py-3">
          <span className="mb-2 block font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">Icon</span>
          <div className="grid grid-cols-6 gap-2">
            {pickableIconNames.map((n) => (
              <button key={n} type="button" onClick={() => setIcon(n)} aria-label={n} aria-pressed={icon === n}
                className={`grid aspect-square place-items-center rounded-xl border ${
                  icon === n ? 'border-accent bg-accent-2 text-accent-ink' : 'border-line bg-bg text-ink-2'
                }`}>
                <DynamicIcon name={n} size={20} />
              </button>
            ))}
          </div>
        </div>

        {/* Fields (trackers only) */}
        {!isCategory && (
          <div className="space-y-2">
            <span className="block font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">Fields</span>
            {fields.length === 0 && (
              <p className="font-body text-[12px] text-ink-3">
                No fields yet — a fieldless tracker just records the time you tapped it. Add fields to capture details.
              </p>
            )}
            {fields.map((f, i) => {
              const preset = (presets.data || []).find((p) => p.slug === f.presetSlug)
              return (
                <div key={i} className="space-y-2 rounded-field border border-line bg-surface px-3 py-2.5">
                  <div className="flex items-center gap-2">
                    <input
                      value={f.name} onChange={(e) => patchField(i, { name: e.target.value })} maxLength={40}
                      className="flex-1 rounded-xl border border-line bg-bg px-3 py-2 font-body text-[14px] text-ink outline-none"
                    />
                    <button type="button" onClick={() => removeField(i)} aria-label="Remove field"
                      className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-line bg-bg text-ink-2">
                      <Close size={14} />
                    </button>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">
                      {preset?.name || f.presetSlug}
                    </span>
                    <div className="flex items-center gap-3">
                      <label className="flex items-center gap-1.5 font-body text-[12px] text-ink-2">
                        <input type="checkbox" checked={f.required}
                          onChange={(e) => patchField(i, { required: e.target.checked })} />
                        Required
                      </label>
                      <button type="button" onClick={() => moveField(i, -1)} disabled={i === 0} aria-label="Move up"
                        className="font-mono text-[13px] text-accent disabled:opacity-30">▲</button>
                      <button type="button" onClick={() => moveField(i, 1)} disabled={i === fields.length - 1} aria-label="Move down"
                        className="font-mono text-[13px] text-accent disabled:opacity-30">▼</button>
                    </div>
                  </div>
                </div>
              )
            })}
            <button type="button" onClick={() => setPicking(true)}
              className="w-full rounded-[14px] border border-dashed border-line bg-surface px-4 py-3 font-display text-[13px] font-bold text-accent">
              + Add field
            </button>
          </div>
        )}

        {error && <p role="alert" className="px-1 font-body text-[13px] text-warn">{error}</p>}
      </main>

      {/* Sticky save bar */}
      <div className="sticky bottom-0 border-t border-line bg-bg px-[18px] py-3">
        <button type="button" onClick={submit} disabled={busy}
          className="w-full rounded-[14px] px-4 py-3 font-display text-[15px] font-bold text-white shadow-md disabled:opacity-50"
          style={{ background: 'linear-gradient(160deg, var(--accent), var(--accent-deep))' }}>
          {busy ? 'Creating…' : `Create ${isCategory ? 'category' : 'tracker'}`}
        </button>
      </div>

      {picking && (
        <PresetPicker
          presets={presets.data || []}
          loading={presets.loading}
          onPick={addField}
          onClose={() => setPicking(false)}
        />
      )}
    </div>
  )
}

// Modal list of the 28 property presets to choose a field's widget.
function PresetPicker({ presets, loading, onPick, onClose }) {
  return (
    <div className="fixed inset-0 z-30 flex items-end justify-center" role="dialog" aria-modal="true" aria-label="Add field">
      <button aria-label="Close" onClick={onClose} className="absolute inset-0 h-full w-full bg-black/40" />
      <div className="relative max-h-[76vh] w-full max-w-[480px] overflow-y-auto rounded-t-3xl border-t border-line bg-surface p-4 pb-8">
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-line" />
        <h2 className="mb-3 font-display text-[18px] font-extrabold text-ink">Pick a field type</h2>
        {loading ? (
          <p className="font-body text-[13px] text-ink-3">Loading…</p>
        ) : (
          <ul className="space-y-2">
            {presets.map((p) => (
              <li key={p.slug}>
                <button onClick={() => onPick(p)}
                  className="flex w-full items-center justify-between rounded-2xl border border-line bg-bg px-3 py-2.5 text-left">
                  <span className="font-body text-[13px] font-semibold text-ink">{p.name}</span>
                  <span className="font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">{p.widget}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
