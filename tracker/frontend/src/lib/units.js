// Measurement unit conversions. Logged data is ALWAYS canonical metric
// (kg / cm / °C) — these helpers only change how a value is shown and entered.
// A measurement's "dimension" is keyed off its preset slug, so only the
// weight / height / temperature trackers are ever converted; every other
// number/dose/duration field is left exactly as-is.

export const DEFAULT_UNITS = { weightUnit: 'kg', heightUnit: 'cm', temperatureUnit: 'c' }

// preset.slug -> dimension
const DIM_BY_SLUG = {
  'weight-kg': 'weight',
  'height-cm': 'height',
  'temperature-c': 'temperature',
}

export function dimensionForPreset(preset) {
  return (preset && DIM_BY_SLUG[preset.slug]) || null
}

export function unitFor(dimension, prefs) {
  const p = prefs || DEFAULT_UNITS
  if (dimension === 'weight') return p.weightUnit || 'kg'
  if (dimension === 'height') return p.heightUnit || 'cm'
  if (dimension === 'temperature') return p.temperatureUnit || 'c'
  return null
}

// A unit is "canonical" when no conversion is needed (it's the stored unit).
export function isCanonicalUnit(dimension, unit) {
  return (
    (dimension === 'weight' && unit === 'kg') ||
    (dimension === 'height' && unit === 'cm') ||
    (dimension === 'temperature' && unit === 'c')
  )
}

// --- Scalar conversions (weight, temperature). Height ft/in is handled with
// the dual-dropdown helpers below, not these. ---

const KG_PER_LB = 0.45359237
const CM_PER_IN = 2.54

export const kgToLb = (kg) => kg / KG_PER_LB
export const lbToKg = (lb) => lb * KG_PER_LB
export const cToF = (c) => (c * 9) / 5 + 32
export const fToC = (f) => ((f - 32) * 5) / 9
export const cmToIn = (cm) => cm / CM_PER_IN
export const inToCm = (inches) => inches * CM_PER_IN

// Decimal places kept when rounding back to canonical, per dimension. Matches
// the metric preset precision so stored values stay clean.
const CANONICAL_DP = { weight: 1, height: 1, temperature: 1 }

function round(n, dp) {
  const f = 10 ** dp
  return Math.round(n * f) / f
}

/** Canonical metric value -> display value in `unit`. */
export function fromCanonical(dimension, unit, value) {
  if (value == null || isCanonicalUnit(dimension, unit)) return value
  if (dimension === 'weight') return round(kgToLb(value), 1)
  if (dimension === 'temperature') return round(cToF(value), 1)
  if (dimension === 'height') return round(cmToIn(value), 1)
  return value
}

/** Display value in `unit` -> canonical metric value. */
export function toCanonical(dimension, unit, value) {
  if (value == null || isCanonicalUnit(dimension, unit)) return value
  const dp = CANONICAL_DP[dimension] ?? 1
  if (dimension === 'weight') return round(lbToKg(value), dp)
  if (dimension === 'temperature') return round(fToC(value), dp)
  if (dimension === 'height') return round(inToCm(value), dp)
  return value
}

// Display-unit stepper config (min/max/step/unit) for the non-canonical scalar
// units. Round, human-friendly ranges rather than ugly converted metric steps.
const STEPPER = {
  'weight:lb': { min: 45, max: 770, step: 0.5, unit: 'lb' },
  'temperature:f': { min: 93, max: 108, step: 0.2, unit: '°F' },
}

export function stepperConfig(dimension, unit, presetOpts) {
  const override = STEPPER[`${dimension}:${unit}`]
  if (!override) return presetOpts
  return { ...override, default: fromCanonical(dimension, unit, presetOpts.default) }
}

// --- Height feet/inches ---

/** Canonical cm -> { ft, in } rounded to the nearest whole inch. */
export function cmToFtIn(cm) {
  const totalIn = Math.round(cmToIn(cm))
  return { ft: Math.floor(totalIn / 12), in: totalIn % 12 }
}

/** { ft, in } -> canonical cm (1 dp). */
export function ftInToCm(ft, inches) {
  return round(inToCm(ft * 12 + inches), 1)
}

/** Inclusive ft range that spans a cm min/max, for the picker dropdown. */
export function ftRange(minCm, maxCm) {
  const lo = Math.floor(cmToIn(minCm) / 12)
  const hi = Math.ceil(cmToIn(maxCm) / 12)
  const out = []
  for (let f = lo; f <= hi; f++) out.push(f)
  return out
}

// --- Read-only display formatting (Me biometrics tiles, etc.) ---

/** Format a canonical measurement for display in the user's unit. */
export function formatMeasurement(dimension, unit, canonicalValue) {
  if (canonicalValue == null) return null
  if (dimension === 'height' && unit === 'ftin') {
    const { ft, in: inches } = cmToFtIn(canonicalValue)
    return `${ft}′${inches}″`
  }
  const v = fromCanonical(dimension, unit, canonicalValue)
  if (dimension === 'weight') return `${v} ${unit}`
  if (dimension === 'height') return `${v} cm`
  if (dimension === 'temperature') return `${v} ${unit === 'f' ? '°F' : '°C'}`
  return `${v}`
}
