// Small presentation helpers shared across screens.

const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export function greeting(date = new Date()) {
  const h = date.getHours()
  if (h < 12) return 'Good morning'
  if (h < 18) return 'Good afternoon'
  return 'Good evening'
}

/** "Thursday · May 20" */
export function dateLabel(date = new Date()) {
  return `${DAYS[date.getDay()]} · ${MONTHS[date.getMonth()]} ${date.getDate()}`
}

/** "8:05 AM" */
export function timeLabel(iso) {
  const d = new Date(iso)
  let h = d.getHours()
  const m = d.getMinutes().toString().padStart(2, '0')
  const ampm = h < 12 ? 'AM' : 'PM'
  h = h % 12 || 12
  return `${h}:${m} ${ampm}`
}

/** Local calendar-day key for grouping, e.g. "2026-05-21". */
export function dayKey(iso) {
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Group heading: "Today" / "Yesterday" / "Tuesday, May 20". */
export function dayHeading(iso) {
  const d = new Date(iso)
  const today = dayKey(new Date())
  const yest = dayKey(new Date(Date.now() - 86400000))
  const k = dayKey(iso)
  if (k === today) return 'Today'
  if (k === yest) return 'Yesterday'
  return `${DAYS[d.getDay()]}, ${MONTHS[d.getMonth()]} ${d.getDate()}`
}

/** Compact one-line summary of an entry's option values for the Today feed. */
export function summarizeOptions(options) {
  if (!options || !options.length) return ''
  return options
    .map((o) => {
      const v = Array.isArray(o.value) ? o.value.join(', ') : o.value
      return o.property ? `${o.property}: ${v}` : `${v}`
    })
    .join(' · ')
}
