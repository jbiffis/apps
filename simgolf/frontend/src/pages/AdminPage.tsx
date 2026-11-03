import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Season, type Round, type Hole } from '../api'
import { HandicapSetup } from '../components/HandicapSetup'
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
  const roundsRequest = useRef(0)

  // Create / edit season form
  const [showSeasonForm, setShowSeasonForm] = useState(false)
  const [seasonYear, setSeasonYear] = useState(new Date().getFullYear().toString())
  const [seasonName, setSeasonName] = useState('')
  const [seasonSystem, setSeasonSystem] = useState<Season['handicap_system']>('best_rounds_v1')
  const [editingSeasonId, setEditingSeasonId] = useState<number | null>(null)
  const [seasonToDelete, setSeasonToDelete] = useState<Season | null>(null)
  const [seasonBusy, setSeasonBusy] = useState(false)
  const [seasonError, setSeasonError] = useState('')

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

  async function loadRounds(sid: number, signal?: AbortSignal) {
    const request = ++roundsRequest.current
    const isCurrent = () => request === roundsRequest.current && !signal?.aborted
    setLoading(true)
    try {
      const [roundList, summaryData] = await Promise.all([
        api.get<Round[]>(`/seasons/${sid}/rounds`),
        api.get<{ tournaments: Array<{ id: number; number: number; name: string }> }>(`/seasons/${sid}/summary`),
      ])
      const withStatus = await Promise.all(roundList.map(r =>
        api.get<{ players: Array<{ holes: Record<string, number | null> }> }>(`/rounds/${r.id}`)
          .then(sc => {
            const filledHoles = sc.players.reduce((sum, p) =>
              sum + Object.values(p.holes || {}).filter(v => v != null).length, 0)
            return { ...r, score_count: filledHoles } as RoundWithStatus
          })
      ))
      if (!isCurrent()) return
      setTournaments(summaryData.tournaments)
      setFormTournamentId(String(summaryData.tournaments[0]?.id ?? ''))
      setRounds(withStatus)
    } catch {
      if (isCurrent()) setMsg('Error loading rounds.')
    } finally {
      if (isCurrent()) setLoading(false)
    }
  }

  useEffect(() => {
    Promise.all([loadSeasons(), loadCourses()]).then(([s]) => {
      if (s.length > 0) {
        const latest = s[0]
        setSeasonId(latest.id)
      } else {
        setLoading(false)
        setShowSeasonForm(true)
      }
    }).catch(() => {
      setMsg('Error loading seasons or courses.')
      setLoading(false)
    })
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setRounds([])
    setTournaments([])
    setFormTournamentId('')
    if (seasonId) void loadRounds(seasonId, controller.signal)
    return () => controller.abort()
  }, [seasonId])

  function openSeasonForm(season?: Season) {
    setEditingSeasonId(season?.id ?? null)
    setSeasonYear(String(season?.year ?? new Date().getFullYear()))
    setSeasonName(season?.name ?? '')
    setSeasonSystem(season?.handicap_system ?? 'best_rounds_v1')
    setSeasonToDelete(null)
    setSeasonError('')
    setShowSeasonForm(true)
  }

  async function saveSeason() {
    if (seasonBusy) return
    const year = Number(seasonYear)
    const name = seasonName.trim() || (editingSeasonId === null ? `${year} Season` : '')
    if (!Number.isInteger(year) || year < 1 || year > 9999 || !name || [...name].length > 100) {
      setSeasonError('Enter a year between 1 and 9999 and a name of 1–100 characters.')
      return
    }
    setSeasonBusy(true)
    setSeasonError('')
    setMsg('')
    try {
      const season = editingSeasonId === null
        ? await api.post<Season>('/seasons', { year, name, handicap_system: seasonSystem })
        : await api.put<Season>(`/seasons/${editingSeasonId}`, { year, name, handicap_system: seasonSystem })
      setSeasons(current => [...current.filter(s => s.id !== season.id), season].sort((a, b) => b.year - a.year))
      setSeasonId(season.id)
      setShowSeasonForm(false)
      setMsg(editingSeasonId === null
        ? `Season "${season.name}" created with 3 tournaments.`
        : `Season "${season.name}" updated.`)
    } catch (error) {
      setSeasonError(error instanceof Error ? error.message : 'Error saving season. Please try again.')
    } finally {
      setSeasonBusy(false)
    }
  }

  async function deleteSeason() {
    if (!seasonToDelete || seasonBusy) return
    setSeasonBusy(true)
    setSeasonError('')
    setMsg('')
    try {
      await api.del(`/seasons/${seasonToDelete.id}`)
      roundsRequest.current++
      const remaining = seasons.filter(s => s.id !== seasonToDelete.id)
      setSeasons(remaining)
      setSeasonId(remaining[0]?.id ?? null)
      setRounds([])
      setTournaments([])
      setShowRoundForm(false)
      setSeasonToDelete(null)
      setMsg(`Season "${seasonToDelete.name}" deleted.`)
      if (remaining.length === 0) {
        setLoading(false)
        openSeasonForm()
      }
    } catch {
      setSeasonError('Error deleting season. Please try again.')
    } finally {
      setSeasonBusy(false)
    }
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
  const editingSeason = seasons.find(s => s.id === editingSeasonId)
  const selectedSeason = seasons.find(s => s.id === seasonId)

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
              aria-label="Season"
              disabled={seasonBusy}
              onChange={e => {
                setSeasonId(Number(e.target.value))
                setShowSeasonForm(false)
                setSeasonToDelete(null)
                setSeasonError('')
                setShowRoundForm(false)
              }}
            >
              {seasons.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          )}
          {seasonId !== null && (
            <>
              <button className={styles.btnOutline} disabled={seasonBusy} onClick={() => {
                const season = seasons.find(s => s.id === seasonId)
                if (season) openSeasonForm(season)
              }}>Edit Season</button>
              <button className={styles.btnDanger} disabled={seasonBusy} onClick={() => {
                setSeasonToDelete(seasons.find(s => s.id === seasonId) ?? null)
                setShowSeasonForm(false)
                setSeasonError('')
              }}>Delete Season</button>
            </>
          )}
          <button className={styles.btnOutline} disabled={seasonBusy} onClick={() => openSeasonForm()}>
            + Season
          </button>
          <Link className={styles.btnOutline} to="/admin/courses">Courses</Link>
          {seasons.length > 0 && courses.length > 0 && (
            <button className={styles.btnCreate} disabled={seasonBusy} onClick={() => setShowRoundForm(v => !v)}>
              + Round
            </button>
          )}
        </div>
      </div>

      {msg && <p className={styles.successMsg} role="status">{msg}</p>}

      {seasonToDelete && (
        <div className={styles.formCard}>
          <h2 className={styles.formTitle}>Delete "{seasonToDelete.name}"?</h2>
          <p>This permanently deletes the season and all its tournaments, rounds, scores, handicaps, prizes, closest-to-pin entries and score edit history. Players and courses are kept. This cannot be undone.</p>
          {seasonError && <p className={styles.errorMsg} role="alert">{seasonError}</p>}
          <div className={styles.formActions}>
            <button className={styles.btnDanger} disabled={seasonBusy} onClick={deleteSeason}>{seasonBusy ? 'Deleting…' : 'Permanently Delete Season'}</button>
            <button className={styles.btnSecondary} disabled={seasonBusy} onClick={() => setSeasonToDelete(null)}>Cancel</button>
          </div>
        </div>
      )}

      {/* No seasons state */}
      {seasons.length === 0 && !loading && (
        <div className={styles.emptyState}>
          <p className={styles.emptyIcon}>⛳</p>
          <p className={styles.emptyText}>No seasons yet. Create a season to get started.</p>
        </div>
      )}

      {/* Create season form */}
      {showSeasonForm && (
        <form className={styles.formCard} onSubmit={e => { e.preventDefault(); void saveSeason() }}>
          <h2 className={styles.formTitle}>{editingSeasonId === null ? 'Create Season' : 'Edit Season'}</h2>
          <div className={styles.formRow}>
            <label className={styles.label}>
              Year
              <input
                type="number"
                className={styles.input}
                value={seasonYear}
                onChange={e => setSeasonYear(e.target.value)}
                min={1}
                max={9999}
                required
                disabled={seasonBusy}
              />
            </label>
            <label className={styles.label}>
              Name {editingSeasonId === null ? '(optional)' : ''}
              <input
                type="text"
                className={styles.input}
                value={seasonName}
                onChange={e => setSeasonName(e.target.value)}
                placeholder={`${seasonYear} Season`}
                maxLength={100}
                required={editingSeasonId !== null}
                disabled={seasonBusy}
              />
            </label>
          </div>
          <div className={styles.label}>
            <label htmlFor="season-handicap-system">Handicap system</label>
            <select id="season-handicap-system" className={styles.select} value={seasonSystem}
              disabled={seasonBusy || editingSeason?.handicap_system_locked}
              onChange={e => setSeasonSystem(e.target.value as Season['handicap_system'])}>
              <option value="best_rounds_v1">Best rounds (new system)</option>
              <option value="legacy">Original system</option>
            </select>
          </div>
          <p className={styles.hint}>{editingSeason?.handicap_system_locked
            ? 'The handicap system is locked because tournament scores have been entered.'
            : 'Choose the system before entering tournament scores. Changing systems clears this season’s calculated handicaps so they can be calculated under the selected rules.'}</p>
          <p className={styles.hint}>{editingSeasonId === null
            ? 'Creates the season with 3 tournaments (T1, T2, T3) automatically.'
            : 'Changes the season name and year. Existing tournaments, rounds and scores are kept.'}</p>
          {seasonError && <p className={styles.errorMsg} role="alert">{seasonError}</p>}
          <div className={styles.formActions}>
            <button className={styles.btnPrimary} type="submit" disabled={seasonBusy}>{seasonBusy ? 'Saving…' : editingSeasonId === null ? 'Create Season' : 'Save Changes'}</button>
            <button className={styles.btnSecondary} type="button" disabled={seasonBusy} onClick={() => setShowSeasonForm(false)}>Cancel</button>
          </div>
        </form>
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
            <button className={styles.btnPrimary} disabled={seasonBusy} onClick={createRound}>Create Round</button>
            <button className={styles.btnSecondary} onClick={() => setShowRoundForm(false)}>Cancel</button>
          </div>
        </div>
      )}

      {/* No courses warning */}
      {selectedSeason?.handicap_system === 'best_rounds_v1' && (
        <HandicapSetup key={selectedSeason.id} seasonId={selectedSeason.id}
          disabled={loading || seasonBusy}
          scoredTournaments={tournaments.filter(t => rounds.some(r => r.tournament_id === t.id && r.score_count > 0)).map(t => t.number)} />
      )}

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
