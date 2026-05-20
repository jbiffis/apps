// Renders one entry field based on its preset widget. Controlled: the parent
// owns the value and gets updates via onChange. `value` types by widget:
//   step/single_select/face_select → option.value (number|string)
//   multi_select                   → array of option.value
//   number/dose/duration           → number
//   text                           → string
//   bool                           → boolean
export default function FieldRenderer({ property, value, onChange }) {
  const preset = property.preset || {}
  const opts = preset.options

  return (
    <div className="rounded-field border border-line bg-surface px-4 py-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">
          {property.name}
          {property.required && <span className="text-warn"> *</span>}
        </span>
      </div>
      {renderWidget(preset.widget, opts, value, onChange)}
    </div>
  )
}

function renderWidget(widget, opts, value, onChange) {
  switch (widget) {
    case 'step':
    case 'single_select':
    case 'face_select':
      return <ChipGroup options={opts || []} selected={value} onPick={onChange} face={widget === 'face_select'} />
    case 'multi_select':
      return <ChipGroup options={opts || []} selected={value} onPick={onChange} multi />
    case 'number':
    case 'dose':
    case 'duration':
      return <Stepper opts={opts || {}} value={value} onChange={onChange} />
    case 'bool':
      return <Toggle opts={opts || {}} value={!!value} onChange={onChange} />
    case 'text':
      return <TextArea opts={opts || {}} value={value || ''} onChange={onChange} />
    default:
      return <p className="font-body text-[12px] text-ink-3">Unsupported field</p>
  }
}

function ChipGroup({ options, selected, onPick, multi = false, face = false }) {
  const isOn = (v) => (multi ? Array.isArray(selected) && selected.includes(v) : selected === v)
  const toggle = (v) => {
    if (!multi) return onPick(v)
    const cur = Array.isArray(selected) ? selected : []
    onPick(cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v])
  }
  return (
    <div className="flex flex-wrap gap-2" role={multi ? 'group' : 'radiogroup'}>
      {options.map((o) => {
        const on = isOn(o.value)
        return (
          <button
            key={String(o.value)}
            type="button"
            role={multi ? 'checkbox' : 'radio'}
            aria-checked={on}
            onClick={() => toggle(o.value)}
            className={`flex items-center gap-1 whitespace-nowrap rounded-full px-3 py-1.5 font-body text-[13px] ${
              on ? 'bg-accent text-white shadow-sm' : 'border border-line bg-surface-2 text-ink-2'
            }`}
          >
            {face && o.emoji && <span aria-hidden>{o.emoji}</span>}
            {o.label ?? o.value}
          </button>
        )
      })}
    </div>
  )
}

function Stepper({ opts, value, onChange }) {
  const min = num(opts.min, 0)
  const max = num(opts.max, Number.MAX_SAFE_INTEGER)
  const step = num(opts.step, 1)
  const v = num(value, num(opts.default, min))
  const round = (n) => Number(Math.min(max, Math.max(min, n)).toFixed(step < 1 ? 2 : 0))
  return (
    <div className="flex items-center gap-3">
      <button type="button" aria-label="Decrease" onClick={() => onChange(round(v - step))}
        className="grid h-10 w-10 place-items-center rounded-xl border border-line bg-surface-2 font-display text-[20px] text-ink-2">−</button>
      <div className="flex flex-1 items-baseline justify-center gap-1">
        <input
          type="number" inputMode="decimal" value={v} min={min} max={max} step={step}
          onChange={(e) => onChange(round(parseFloat(e.target.value) || 0))}
          className="w-20 bg-transparent text-center font-display text-[22px] font-extrabold text-ink outline-none"
        />
        {opts.unit && <span className="font-body text-[13px] text-ink-3">{opts.unit}</span>}
      </div>
      <button type="button" aria-label="Increase" onClick={() => onChange(round(v + step))}
        className="grid h-10 w-10 place-items-center rounded-xl border border-line bg-surface-2 font-display text-[20px] text-ink-2">+</button>
    </div>
  )
}

function Toggle({ opts, value, onChange }) {
  return (
    <div className="flex gap-2">
      {[true, false].map((b) => (
        <button key={String(b)} type="button" onClick={() => onChange(b)}
          className={`flex-1 rounded-xl px-3 py-2 font-body text-[13px] ${
            value === b ? 'bg-accent text-white' : 'border border-line bg-surface-2 text-ink-2'
          }`}>
          {b ? (opts.trueLabel || 'Yes') : (opts.falseLabel || 'No')}
        </button>
      ))}
    </div>
  )
}

function TextArea({ opts, value, onChange }) {
  return (
    <textarea
      value={value}
      maxLength={opts.maxLength || 5000}
      placeholder={opts.placeholder || ''}
      onChange={(e) => onChange(e.target.value)}
      rows={3}
      className="w-full resize-none bg-transparent font-body text-[14px] text-ink outline-none placeholder:text-ink-3"
    />
  )
}

function num(v, d) {
  return typeof v === 'number' ? v : (typeof v === 'string' && v !== '' && !isNaN(+v) ? +v : d)
}
