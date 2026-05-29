// Comprehensive test-data seeder. Logs in as each test user, reads their
// (gender-filtered) catalog from the API, then generates ~90 days of realistic
// logged events with plausible per-property values — all through the real REST
// API, so it exercises the same validation + privacy scoping the app uses.
//
// Idempotency: run.sh TRUNCATEs logged_events before invoking this, so re-runs
// regenerate a fresh dataset rather than piling on.
//
// Usage: node seed.mjs   (run.sh sets sensible defaults / waits for health)
//   TRACKER_TEST_API   default http://localhost:18080/tracker/api
//   TRACKER_TEST_DAYS  default 90
//   TRACKER_TEST_USERS default carley401@gmail.com,jeremy@biffis.com,morgan@biffis.com,dave@biffis.com
//   TRACKER_TEST_PW     default test123

const BASE = process.env.TRACKER_TEST_API || 'http://localhost:18080/tracker/api'
const DAYS = parseInt(process.env.TRACKER_TEST_DAYS || '90', 10)
const USERS = (process.env.TRACKER_TEST_USERS || 'carley401@gmail.com,jeremy@biffis.com,morgan@biffis.com,dave@biffis.com').split(',')
const PASSWORD = process.env.TRACKER_TEST_PW || 'test123'
const CONCURRENCY = 16

// Mean events per day. Slug overrides win; otherwise the leaf's top-level
// category sets the cadence; otherwise the global default. <1 = occasional.
const SLUG_FREQ = {
  water: 6, mood: 2, sleep: 1, steps: 1, coffee: 1.5,
  weight: 0.14, journal: 0.3, headache: 0.18, workout: 0.5,
}
const CATEGORY_FREQ = {
  food: 2.5, medication: 1.2, recreational: 0.3, activity: 0.6,
  health: 0.1, 'lady-stuff': 0.15, journal: 0.3,
}
const DEFAULT_FREQ = 0.08

const NOTES = [
  'felt fine', 'a bit tired', 'after lunch', 'before bed', 'busy day',
  'rough morning', 'great session', 'forgot earlier', 'with water', 'quick one',
]
const JOURNAL = [
  'Slept poorly but pushed through the morning. Walk at lunch helped.',
  'Good day overall. Energy was steady and the headache stayed away.',
  'Stressful afternoon — too much coffee, paid for it later.',
  'Felt strong in the workout. Knees a little sore after.',
  'Quiet evening. Read a bit, early night.',
  'Travel day. Ate out, water intake was low.',
  'Productive. Kept up with meds and hydration for once.',
]

// ---- helpers ----------------------------------------------------------------
const rnd = () => Math.random()
const randInt = (a, b) => a + Math.floor(rnd() * (b - a + 1))
const pick = (arr) => arr[Math.floor(rnd() * arr.length)]
const chance = (p) => rnd() < p
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v))
const num = (v, d) => (typeof v === 'number' ? v : d)
function sample(arr, n) {
  const c = [...arr]
  const out = []
  while (out.length < n && c.length) out.push(c.splice(Math.floor(rnd() * c.length), 1)[0])
  return out
}
// Bias an ordered list of numeric option-values low or high.
function weighted(vals, low) {
  const r = low ? rnd() ** 1.8 : 1 - rnd() ** 1.8
  return vals[clamp(Math.floor(r * vals.length), 0, vals.length - 1)]
}

async function login(email) {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD }),
  })
  if (!res.ok) throw new Error(`login ${email} → ${res.status}`)
  return res.json()
}

async function catalog(token) {
  const res = await fetch(`${BASE}/event-types`, { headers: { Authorization: `Bearer ${token}` } })
  if (!res.ok) throw new Error(`catalog → ${res.status}`)
  return res.json()
}

// Walk the tree → flat list of leaves, tagging each with its top-level category.
function leaves(tree) {
  const out = []
  const walk = (node, top) => {
    const cat = top || node.slug
    if (node.isCategory) {
      for (const c of node.children || []) walk(c, cat)
    } else {
      out.push({ slug: node.slug, name: node.name, category: cat, properties: node.properties || [] })
    }
  }
  for (const root of tree) walk(root, null)
  return out
}

function freqFor(leaf) {
  if (leaf.slug in SLUG_FREQ) return SLUG_FREQ[leaf.slug]
  if (leaf.category in CATEGORY_FREQ) return CATEGORY_FREQ[leaf.category]
  return DEFAULT_FREQ
}

function synthValue(preset) {
  const opts = preset.options
  switch (preset.widget) {
    case 'step':
      return weighted((opts || []).map((o) => o.value), true)
    case 'face_select':
      return weighted((opts || []).map((o) => o.value), false)
    case 'single_select':
      return pick(opts).value
    case 'multi_select': {
      const vals = (opts || []).map((o) => o.value)
      return sample(vals, randInt(1, Math.min(3, vals.length)))
    }
    case 'number':
    case 'dose':
    case 'duration': {
      const o = opts || {}
      const min = num(o.min, 0)
      const max = num(o.max, 10)
      const step = num(o.step, 1)
      const def = num(o.default, (min + max) / 2)
      const spread = Math.max(step, (max - min) * 0.15)
      let v = clamp(def + (rnd() * 2 - 1) * spread, min, max)
      v = Math.round(v / step) * step
      return Number(v.toFixed(step < 1 ? 2 : 0))
    }
    case 'text':
      return null // free notes handled separately; leave text props mostly empty
    case 'bool':
      return chance(0.7)
    default:
      return null
  }
}

function buildBody(leaf, when) {
  const options = []
  for (const p of leaf.properties) {
    const value = synthValue(p.preset)
    if (value !== null && value !== undefined) options.push({ propertyName: p.name, value })
  }
  const body = { eventTypeSlug: leaf.slug, occurredAt: when.toISOString() }
  if (options.length) body.options = options
  if (leaf.slug === 'journal') body.note = pick(JOURNAL)
  else if (chance(0.12)) body.note = pick(NOTES)
  return body
}

// A timestamp on `day` (0 = today) at a plausible hour for the tracker.
function timeOn(daysAgo, slug) {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  let hour
  if (slug === 'sleep') hour = randInt(5, 8)
  else if (slug === 'coffee') hour = pick([7, 8, 9, 13, 14])
  else hour = randInt(7, 22)
  d.setHours(hour, randInt(0, 59), randInt(0, 59), 0)
  if (daysAgo === 0 && d > new Date()) d.setTime(Date.now() - randInt(1, 90) * 60000)
  return d
}

async function runPool(tasks) {
  let i = 0
  let done = 0
  await Promise.all(
    Array.from({ length: Math.min(CONCURRENCY, tasks.length) }, async () => {
      while (i < tasks.length) {
        const idx = i++
        await tasks[idx]()
        if (++done % 500 === 0) process.stdout.write(`\r  posted ${done}/${tasks.length}`)
      }
    }),
  )
  process.stdout.write(`\r  posted ${tasks.length}/${tasks.length}\n`)
}

async function main() {
  console.log(`Seeding ${DAYS} days for ${USERS.join(', ')} → ${BASE}`)
  const tasks = []
  const perUser = {}

  for (const email of USERS) {
    const { token, user } = await login(email)
    const ls = leaves(await catalog(token))
    let count = 0
    for (let daysAgo = DAYS - 1; daysAgo >= 0; daysAgo--) {
      const todayFactor = daysAgo === 0 ? new Date().getHours() / 24 : 1
      for (const leaf of ls) {
        const f = freqFor(leaf) * todayFactor
        let n = Math.floor(f) + (chance(f - Math.floor(f)) ? 1 : 0)
        while (n-- > 0) {
          const body = buildBody(leaf, timeOn(daysAgo, leaf.slug))
          tasks.push(() => postEvent(token, body))
          count++
        }
      }
    }
    perUser[user.email] = count
  }

  console.log('Planned entries:', perUser, '→ total', tasks.length)
  await runPool(tasks)
  console.log('Done.')
}

async function postEvent(token, body) {
  const res = await fetch(`${BASE}/logged-events`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok && res.status !== 201) {
    const t = await res.text()
    throw new Error(`POST ${res.status}: ${t.slice(0, 200)} :: ${JSON.stringify(body).slice(0, 160)}`)
  }
}

main().catch((e) => {
  console.error('\nSeed failed:', e.message)
  process.exit(1)
})
