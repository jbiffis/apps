import { api } from '../api.js'

// Pull the caller's entire logged-events history (paginated via the keyset
// cursor) for export. Caps pages defensively so a runaway never hangs the tab.
export async function fetchAllEvents({ maxPages = 200 } = {}) {
  const from = new Date('2000-01-01T00:00:00Z').toISOString()
  const to = new Date().toISOString()
  const base = `/logged-events?from=${from}&to=${to}&limit=200`
  const out = []
  let cursor = null
  for (let i = 0; i < maxPages; i++) {
    const r = await api.get(cursor ? `${base}&cursor=${encodeURIComponent(cursor)}` : base)
    out.push(...(r.events || []))
    cursor = r.nextCursor
    if (!cursor) break
  }
  return out
}

// One flat row per option (or one row with empty value if an entry has none),
// so the CSV is tidy in a spreadsheet.
function toCsv(events) {
  const header = ['id', 'event_type', 'occurred_at', 'property', 'value', 'note']
  const esc = (v) => {
    const s = v == null ? '' : String(Array.isArray(v) ? v.join('; ') : v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const rows = [header.join(',')]
  for (const e of events) {
    const base = [e.id, e.eventType?.slug || '', e.occurredAt, '', '', e.note || '']
    const opts = e.options || []
    if (opts.length === 0) {
      rows.push(base.map(esc).join(','))
    } else {
      for (const o of opts) {
        rows.push([e.id, e.eventType?.slug || '', e.occurredAt, o.property || '', o.value, e.note || ''].map(esc).join(','))
      }
    }
  }
  return rows.join('\n')
}

function download(filename, text, type) {
  const blob = new Blob([text], { type })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

const stamp = () => new Date().toISOString().slice(0, 10)

export async function exportJson() {
  const events = await fetchAllEvents()
  download(`lifetracker-export-${stamp()}.json`, JSON.stringify(events, null, 2), 'application/json')
  return events.length
}

export async function exportCsv() {
  const events = await fetchAllEvents()
  download(`lifetracker-export-${stamp()}.csv`, toCsv(events), 'text/csv')
  return events.length
}
