import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Season, type Round } from '../api'
import styles from './AdminPage.module.css'

interface Course {
  id: number
  name: string
}

interface RoundWithStatus extends Round {
  score_count: number
}

export function AdminPage() {
  const [seasons, setSeasons] = useState<Season[]>([])
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [rounds, setRounds] = useState<RoundWithStatus[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)

  // Create round form
  const [showForm, setShowForm] = useState(false)
  const [formCourseId, setFormCourseId] = useState('')
  const [formNine, setFormNine] = useState<'front' | 'back'>('front')
  const [formDate, setFormDate] = useState(new Date().toISOString().slice(0, 10))
  const [formIsPractice, setFormIsPractice] = useState(false)
  const [formTournamentId, setFormTournamentId] = useState('')
  const [formCtpHole, setFormCtpHole] = useState('')
  const [formCtpYardage, setFormCtpYardage] = useState('')
  const [formMsg, setFormMsg] = useState('')
  const [tournaments, setTournaments] = useState<Array<{ id: number; number: number; name: string }>>([])

  useEffect(() => {
    Promise.all([
      api.get<Season[]>('/seasons'),
      api.get<Course[]>('/courses'),
    ]).then(([s, c]) => {
      setSeasons(s)
      setCourses(c)
      if (s.length > 0) setSeasonId(s[s.length - 1].id)
      if (c.length > 0) setFormCourseId(String(c[0].id))
    }).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!seasonId) return
    setLoading(true)
    Promise.all([
      api.get<{ rounds: Round[] }>(`/seasons/${seasonId}/rounds`),
      api.get<{ tournaments: Array<{ id: number; number: number; name: string }> }>(`/seasons/${seasonId}/summary`),
    ]).then(([roundsData, summaryData]) => {
      // Fetch score counts for each round
      const roundList = roundsData.rounds
      Promise.all(roundList.map(r =>
        api.get<{ players: Array<{ holes: Record<string, number | null> }> }>(`/rounds/${r.id}`)
          .then(sc => {
            const filledHoles = sc.players.reduce((sum, p) => {
              return sum + Object.values(p.holes || {}).filter(v => v != null).length
            }, 0)
            return { ...r, score_count: filledHoles } as RoundWithStatus
          })
      )).then(withStatus => {
        setRounds(withStatus)
      })
      setTournaments(summaryData.tournaments)
      if (summaryData.tournaments.length > 0) setFormTournamentId(String(summaryData.tournaments[0].id))
    }).finally(() => setLoading(false))
  }, [seasonId])

  async function createRound() {
    if (!seasonId || !formCourseId) return
    setFormMsg('')
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
      setFormMsg('Round created!')
      setShowForm(false)
      // Refresh rounds
      const roundsData = await api.get<{ rounds: Round[] }>(`/seasons/${seasonId}/rounds`)
      const roundList = roundsData.rounds
      const withStatus = await Promise.all(roundList.map(r =>
        api.get<{ players: Array<{ holes: Record<string, number | null> }> }>(`/rounds/${r.id}`)
          .then(sc => {
            const filledHoles = sc.players.reduce((sum, p) =>
              sum + Object.values(p.holes || {}).filter(v => v != null).length, 0)
            return { ...r, score_count: filledHoles } as RoundWithStatus
          })
      ))
      setRounds(withStatus)
    } catch (e) {
      setFormMsg('Error creating round.')
    }
  }

  if (loading) return <p className={styles.loading}>Loading…</p>

  const practiceRounds = rounds.filter(r => r.is_practice)
  const tournamentRounds = rounds.filter(r => !r.is_practice)

  function RoundRow({ r }: { r: RoundWithStatus }) {
    const hasScores = r.score_count > 0
    const label = r.is_practice ? 'Practice' : `T${tournaments.find(t => t.id === r.tournament_id)?.number ?? '?'}`
    return (
      <tr key={r.id}>
        <td className={styles.tdLabel}>{label}</td>
        <td className={styles.tdRound}>Rd {r.round_number}</td>
        <td className={styles.tdCourse}>{r.course_name} · {r.nine}</td>
        <td className={styles.tdDate}>{r.played_date}</td>
        <td className={styles.tdStatus}>
          {hasScores
            ? <span className={styles.statusDone}>✓ {r.score_count} scores</span>
            : <span className={styles.statusEmpty}>No scores</span>
          }
        </td>
        <td className={styles.tdActions}>
          <Link className={styles.btnEnter} to={`/admin/rounds/${r.id}/scores`}>
            {hasScores ? 'Edit' : 'Enter'} Scores
          </Link>
          <Link className={styles.btnLive} to={`/in-round/${r.id}`}>
            Live
          </Link>
          <Link className={styles.btnView} to={`/round/${r.id}`}>
            View
          </Link>
        </td>
      </tr>
    )
  }

  return (
    <div className={styles.page}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>Admin</h1>
        <div className={styles.controls}>
          <select
            className={styles.select}
            value={seasonId ?? ''}
            onChange={e => setSeasonId(Number(e.target.value))}
          >
            {seasons.map(s => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
          <button className={styles.btnCreate} onClick={() => setShowForm(true)}>
            + New Round
          </button>
        </div>
      </div>

      {formMsg && <p className={styles.successMsg}>{formMsg}</p>}

      {showForm && (
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
                  {tournaments.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              </label>
            )}
            <label className={styles.label}>
              CTP Hole (optional)
              <input type="number" min={1} max={9} className={styles.input} value={formCtpHole} onChange={e => setFormCtpHole(e.target.value)} placeholder="e.g. 5" />
            </label>
            <label className={styles.label}>
              CTP Yardage (optional)
              <input type="number" min={1} className={styles.input} value={formCtpYardage} onChange={e => setFormCtpYardage(e.target.value)} placeholder="e.g. 166" />
            </label>
          </div>
          <div className={styles.formActions}>
            <button className={styles.btnPrimary} onClick={createRound}>Create</button>
            <button className={styles.btnSecondary} onClick={() => setShowForm(false)}>Cancel</button>
          </div>
        </div>
      )}

      {practiceRounds.length > 0 && (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Practice Rounds</h2>
          <div className="table-scroll">
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Round</th>
                  <th>Course</th>
                  <th>Date</th>
                  <th>Scores</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {practiceRounds.map(r => <RoundRow key={r.id} r={r} />)}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {tournamentRounds.length > 0 && (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Tournament Rounds</h2>
          <div className="table-scroll">
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Round</th>
                  <th>Course</th>
                  <th>Date</th>
                  <th>Scores</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {tournamentRounds.map(r => <RoundRow key={r.id} r={r} />)}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {rounds.length === 0 && !loading && (
        <p className={styles.empty}>No rounds yet. Create one above.</p>
      )}
    </div>
  )
}
