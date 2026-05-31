import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useApi } from '../hooks/useApi.js'
import { DEFAULT_UNITS } from '../lib/units.js'
import { Back } from '../icons/index.jsx'

const WEIGHT_OPTS = [['kg', 'Kilograms (kg)'], ['lb', 'Pounds (lb)']]
const HEIGHT_OPTS = [['cm', 'Centimetres (cm)'], ['ftin', 'Feet / inches']]
const TEMP_OPTS = [['c', 'Celsius (°C)'], ['f', 'Fahrenheit (°F)']]

export default function Settings() {
  const navigate = useNavigate()
  const { data, loading } = useApi('/me/preferences')

  const [form, setForm] = useState(DEFAULT_UNITS)
  const [saved, setSaved] = useState(false)

  // Sync the form from the server once it loads (render-phase, per the React
  // "adjusting state on data change" pattern this codebase uses elsewhere).
  const [syncedFrom, setSyncedFrom] = useState(null)
  if (data && data !== syncedFrom) {
    setSyncedFrom(data)
    setForm({ ...DEFAULT_UNITS, ...data })
  }

  // Each change saves immediately (full replace) with an optimistic update;
  // revert the single field on failure.
  function change(key, value) {
    const prev = form[key]
    const next = { ...form, [key]: value }
    setForm(next)
    setSaved(false)
    api.put('/me/preferences', next)
      .then(() => { setSaved(true); setTimeout(() => setSaved(false), 1500) })
      .catch(() => setForm((f) => ({ ...f, [key]: prev })))
  }

  return (
    <div className="mx-auto flex min-h-full max-w-[480px] flex-col bg-bg">
      <header className="flex items-center gap-3 px-[18px] pb-2.5 pt-3">
        <button onClick={() => navigate(-1)} aria-label="Back"
          className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2">
          <Back size={18} />
        </button>
        <h1 className="font-display text-[22px] font-extrabold text-ink">Settings</h1>
        {saved && <span className="ml-auto font-body text-[12px] text-accent">Saved ✓</span>}
      </header>

      <main className="flex-1 space-y-3 overflow-y-auto px-[18px] pb-10">
        <section className="space-y-3">
          <div>
            <h2 className="font-display text-[15px] font-bold text-ink">Units</h2>
            <p className="font-body text-[11px] text-ink-3">
              Changes how measurements are shown and entered. Your logged data isn’t affected.
            </p>
          </div>

          <div className="space-y-3 rounded-qcard border border-line bg-surface p-4">
            <Field label="Weight">
              <Select value={form.weightUnit} onChange={(v) => change('weightUnit', v)} opts={WEIGHT_OPTS} disabled={loading} />
            </Field>
            <Field label="Height">
              <Select value={form.heightUnit} onChange={(v) => change('heightUnit', v)} opts={HEIGHT_OPTS} disabled={loading} />
            </Field>
            <Field label="Temperature">
              <Select value={form.temperatureUnit} onChange={(v) => change('temperatureUnit', v)} opts={TEMP_OPTS} disabled={loading} />
            </Field>
          </div>
        </section>
      </main>
    </div>
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

function Select({ value, onChange, opts, disabled }) {
  return (
    <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-[14px] border border-line bg-surface px-3 py-2.5 font-body text-[14px] text-ink disabled:opacity-50">
      {opts.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
    </select>
  )
}
