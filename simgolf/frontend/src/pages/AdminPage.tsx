import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Season, type Round, type Hole } from '../api'
import styles from './AdminPage.module.css'

interface Course { id: number; name: string; holes: Hole[] }
interface RoundWithStatus extends Round { score_count: number }

export function AdminPage() {
  const [seasons, setSeasons] = useState<Season[]>([])
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [rounds, setRounds] = useState<RoundWithStatus[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [msg, setMsg] = useState('')

  // Create season form
  const [showSeasonForm, setShowSeasonForm] = useState(false)
  const [seasonYear, setSeasonYear] = useState(new Date().getFullYear().toString())
  const [seasonName, setSeasonName] = useState('')

  // Create round form
  const [showRoundForm, setShowRoundForm] = useState(false)
  const [formCourseId, setFormCourseId] = useState('')
  const [formNine, setFormNine] = useState<'front' | 'back'>('front')
  const [formDate, setFormDate] = useState(new Date().toISOString().slice(0, 10))
  const [formIsPractice, setFormIsPractice] = useState(false)
  const [formTournamentId, setFormTournamentId] = useState('')
  const [formCtpHole, setFormCtpHole] = useState('')
  const [formCtpYardage, setFormCtpYardage] = useState('')
  const [tournaments, setTournaments] = useState<Array<{ id: number; number: number; name: string }>>([])

  async function loadSeasons() {
    const s = await api.get<Season[]>('/seasons')
    setSeasons(s)
    return s
  }

  async function loadCourses() {
    const c = await api.get<Course[]>('/courses')
    setCourses(c)
    if (c.length > 0) setFormCourseId(String(c[0].id))
    return c
  }

  async function loadRounds(sid: number) {
    setLoading(true)
    try {
      const [roundList, summaryData] = await Promise.all([
        api.get<Round[]>(`/seasons/${sid}/rounds`),
        api.get<{ tournaments: Array<{ id: number; number: number; name: string }> }>(`/seasons/${sid}/summary`),
      ])
      setTournaments(summaryData.tournaments)
      if (summaryData.tournaments.length > 0) setFormTournamentId(String(summaryData.tournaments[0].id))

      const withStatus = await Promise.all(roundList.map(r =>
        api.get<{ players: Array<{ holes: Record<string, number | null> }> }>(`/rounds/${r.id}`)
          .then(sc => {
            const filledHoles = sc.players.reduce((sum, p) =>
              sum + Object.values(p.holes || {}).filter(v => v != null).length, 0)
            return { ...r, score_count: filledHoles } as RoundWithStatus
          })
      ))
      setRounds(withStatus)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    Promise.all([loadSeasons(), loadCourses()]).then(([s]) => {
      if (s.length > 0) {
        const latest = s[s.length - 1]
        setSeasonId(latest.id)
        loadRounds(latest.id)
      } else {
        setLoading(false)
        setShowSeasonForm(true)
      }
    })
  }, [])

  useEffect(() => {
    if (seasonId) loadRounds(seasonId)
  }, [seasonId])

  async function createSeason() {
    const year = parseInt(seasonYear)
    if (!year) return
    setMsg('')
    try {
      const s = await api.post<Season>('/seasons', { year, name: seasonName || `${year} Season` })
      const updated = await loadSeasons()
      setSeasonId(s.id)
      setShowSeasonForm(false)
      setMsg(`Season "${updated.find(x => x.id === s.id)?.name}" created with 3 tournaments.`)
    } catch { setMsg('Error creating season.') }
  }

  async function createRound() {
    if (!seasonId || !formCourseId) return
    setMsg('')
    try {
      await api.post('/rounds', {
        season_id: seasonId,
        tournament_id: formIsPractice ? null : (formTournamentId ? Number(formTournamentId) : null),
        course_id: Number(formCourseId),
        nine: formNine,
        played_date: formDate,
        is_practice: formIsPractice,
        ctp_hole: formCtpHole ? Number(formCtpHole) : null,
        ctp_yardage: formCtpYardage ? Number(formCtpYardage) : null,
      })
      setShowRoundForm(false)
      setMsg('Round created!')
      if (seasonId) loadRounds(seasonId)
    } catch { setMsg('Error creating round.') }
  }

  const practiceRounds = rounds.filter(r => r.is_practice).sort((a, b) => a.round_number - b.round_number)
  const tournamentRounds = rounds.filter(r => !r.is_practice).sort((a, b) => a.round_number - b.round_number)

  function RoundRow({ r }: { r: RoundWithStatus }) {
    const hasScores = r.score_count > 0
    const tNum = tournaments.find(t => t.id === r.tournament_id)?.number
    const label = r.is_practice ? 'Practice' : `T${tNum ?? '?'}`
    return (
      <tr>
        <td className={styles.tdLabel}>{label}</td>
        <td className={styles.tdRound}>Rd {r.round_number}</td>
        <td className={styles.tdCourse}>{r.course_name} · {r.nine}</td>
        <td className={styles.tdDate}>{r.played_date}</td>
        <td className={styles.tdStatus}>
          {hasScores
            ? <span className={styles.statusDone}>✓ {r.score_count} scores</span>
            : <span className={styles.statusEmpty}>No scores</span>}
        </td>
        <td className={styles.tdActions}>
          <Link className={styles.btnEnter} to={`/admin/rounds/${r.id}/scores`}>
            {hasScores ? 'Edit' : 'Enter'} Scores
          </Link>
          <Link className={styles.btnLive} to={`/in-round/${r.id}`}>Live</Link>
          <Link className={styles.btnView} to={`/round/${r.id}`}>View</Link>
        </td>
      </tr>
    )
  }

  return (
    <div className={styles.page}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>Admin</h1>
        <div className={styles.controls}>
          {seasons.length > 0 && (
            <select
              className={styles.select}
              value={seasonId ?? ''}
              onChange={e => setSeasonId(Number(e.target.value))}
            >
              {seasons.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          )}
          <button className={styles.btnOutline} onClick={() => setShowSeasonForm(v => !v)}>
            + Season
          </button>
          <Link className={styles.btnOutline} to="/admin/courses">Courses</Link>
          {seasons.length > 0 && courses.length > 0 && (
            <button className={styles.btnCreate} onClick={() => setShowRoundForm(v => !v)}>
              + Round
            </button>
          )}
        </div>
      </div>

      {msg && <p className={styles.successMsg}>{msg}</p>}

      {/* No seasons state */}
      {seasons.length === 0 && !loading && (
        <div className={styles.emptyState}>
          <p className={styles.emptyIcon}>⛳</p>
          <p className={styles.emptyText}>No seasons yet. Create a season to get started.</p>
        </div>
      )}

      {/* Create season form */}
      {showSeasonForm && (
        <div className={styles.formCard}>
          <h2 className={styles.formTitle}>Create Season</h2>
          <div className={styles.formRow}>
            <label className={styles.label}>
              Year
              <input
                type="number"
                className={styles.input}
                value={seasonYear}
                onChange={e => setSeasonYear(e.target.value)}
                min={2020}
                max={2040}
              />
            </label>
            <label className={styles.label}>
              Name (optional)
              <input
                type="text"
                className={styles.input}
                value={seasonName}
                onChange={e => setSeasonName(e.target.value)}
                placeholder={`${seasonYear} Season`}
              />
            </label>
          </div>
          <p className={styles.hint}>Creates the season with 3 tournaments (T1, T2, T3) automatically.</p>
          <div className={styles.formActions}>
            <button className={styles.btnPrimary} onClick={createSeason}>Create Season</button>
            <button className={styles.btnSecondary} onClick={() => setShowSeasonForm(false)}>Cancel</button>
          </div>
        </div>
      )}

      {/* Create round form */}
      {showRoundForm && seasons.length > 0 && courses.length > 0 && (
        <div className={styles.formCard}>
          <h2 className={styles.formTitle}>Create Round</h2>
          <div className={styles.formGrid}>
            <label className={styles.label}>
              Course
              <select className={styles.select} value={formCourseId} onChange={e => setFormCourseId(e.target.value)}>
                {courses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <label className={styles.label}>
              Nine
              <select className={styles.select} value={formNine} onChange={e => setFormNine(e.target.value as 'front' | 'back')}>
                <option value="front">Front</option>
                <option value="back">Back</option>
              </select>
            </label>
            <label className={styles.label}>
              Date
              <input type="date" className={styles.input} value={formDate} onChange={e => setFormDate(e.target.value)} />
            </label>
            <label className={styles.checkLabel}>
              <input type="checkbox" checked={formIsPractice} onChange={e => setFormIsPractice(e.target.checked)} />
              Practice round
            </label>
            {!formIsPractice && (
              <label className={styles.label}>
                Tournament
                <select className={styles.select} value={formTournamentId} onChange={e => setFormTournamentId(e.target.value)}>
                  {tournaments.map(t => <option key={t.id} value={t.id}>Tournament {t.number}</option>)}
                </select>
              </label>
            )}
            <label className={styles.label}>
              CTP Hole
              <input type="number" min={1} max={9} className={styles.input} value={formCtpHole} onChange={e => setFormCtpHole(e.target.value)} placeholder="optional" />
            </label>
            <label className={styles.label}>
              CTP Yardage
              <input type="number" min={1} className={styles.input} value={formCtpYardage} onChange={e => setFormCtpYardage(e.target.value)} placeholder="optional" />
            </label>
          </div>
          <div className={styles.formActions}>
            <button className={styles.btnPrimary} onClick={createRound}>Create Round</button>
            <button className={styles.btnSecondary} onClick={() => setShowRoundForm(false)}>Cancel</button>
          </div>
        </div>
      )}

      {/* No courses warning */}
      {seasons.length > 0 && courses.length === 0 && !loading && (
        <div className={styles.warningBox}>
          <strong>No courses yet.</strong> <Link to="/admin/courses">Add a course</Link> before creating rounds.
        </div>
      )}

      {/* Rounds tables */}
      {loading && <p className={styles.loading}>Loading…</p>}

      {!loading && practiceRounds.length > 0 && (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Practice Rounds</h2>
          <div className="table-scroll">
            <table className={styles.table}>
              <thead><tr><th>Type</th><th>Round</th><th>Course</th><th>Date</th><th>Scores</th><th>Actions</th></tr></thead>
              <tbody>{practiceRounds.map(r => <RoundRow key={r.id} r={r} />)}</tbody>
            </table>
          </div>
        </section>
      )}

      {!loading && tournamentRounds.length > 0 && (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Tournament Rounds</h2>
          <div className="table-scroll">
            <table className={styles.table}>
              <thead><tr><th>Type</th><th>Round</th><th>Course</th><th>Date</th><th>Scores</th><th>Actions</th></tr></thead>
              <tbody>{tournamentRounds.map(r => <RoundRow key={r.id} r={r} />)}</tbody>
            </table>
          </div>
        </section>
      )}

      {!loading && seasons.length > 0 && rounds.length === 0 && (
        <p className={styles.empty}>No rounds yet for this season. Click <strong>+ Round</strong> to add one.</p>
      )}
    </div>
  )
}
