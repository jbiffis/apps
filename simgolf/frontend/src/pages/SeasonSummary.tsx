import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type SeasonSummary, type Tournament, type Round } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './SeasonSummary.module.css'

function ArrowUpRight() {
  return <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m4 12 8-8M4 4h8v8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
}

function roundLabel(r: Round): string {
  return r.is_practice ? `Practice Round ${r.round_number}` : `Tournament ${r.tournament_number} Round ${r.round_number}`
}

function courseLabel(r: Round): string {
  const nine = r.nine === 'front' ? 'Front' : 'Back'
  return `${r.course_name} - ${nine}`
}

export function SeasonSummaryPage() {
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [data, setData] = useState<SeasonSummary | null>(null)
  const [rounds, setRounds] = useState<Round[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const selectSeason = useCallback((id: number) => {
    setSeasonId(id)
    setData(null)
    setRounds(null)
    setError('')
    setLoading(true)
  }, [])

  useEffect(() => {
    if (!seasonId) return
    let active = true
    Promise.all([
      api.get<SeasonSummary>(`/seasons/${seasonId}/summary`),
      api.get<Round[]>(`/seasons/${seasonId}/rounds`),
    ])
      .then(([summary, roundList]) => { if (active) { setData(summary); setRounds(roundList) } })
      .catch(() => { if (active) setError('Unable to load standings. Please try again.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [seasonId])

  return (
    <div>
      <div className={styles.heading}>
        <div>
          <h1 className={styles.title}>Season standings</h1>
          <p className="page-description">Tournament points and overall rankings.</p>
        </div>
        <SeasonSelect value={seasonId} onChange={selectSeason} />
      </div>

      {loading && <p className={styles.loading} role="status">Loading standings…</p>}
      {error && <p className="empty-panel" role="alert">{error}</p>}

      {data && <>
        <p className={styles.pointsLabel}>Ranked by total points</p>
        {data.players.length === 0 ? (
          <div className="empty-panel"><h3>The board is ready.</h3><p>Standings will appear once this season’s scores are entered.</p><Link to="/admin">Go to season admin →</Link></div>
        ) : (
          <div className="table-scroll" tabIndex={0} role="region" aria-label="Season standings table">
            <table className={`${styles.table} table-sticky`}>
              <thead><tr>
                <th className={styles.thPos} scope="col"><span className={styles.rankLabel}>Rank</span></th>
                <th className={styles.thName} scope="col">Player</th>
                {data.tournaments.map((t: Tournament) => <th key={t.id} className={styles.thTournament} scope="col"><Link to={`/tournament/${t.id}`} aria-label={`Tournament ${t.number}`}>T{t.number} <ArrowUpRight /></Link></th>)}
                <th className={styles.thTotal} scope="col">Total pts</th>
              </tr></thead>
              <tbody>{data.players.map(row => <tr key={row.player_id} className={row.position === 1 ? styles.leader : undefined}>
                <td className={styles.tdPos}><span className={styles.rank}>{String(row.position).padStart(2, '0')}</span></td>
                <td className={styles.tdName}><Link to={`/player/${row.player_id}?season=${seasonId}`}>{row.name}</Link>{row.position === 1 && <span className={styles.leaderTag}>Leading</span>}</td>
                {data.tournaments.map((t: Tournament) => <td key={t.id} className={styles.tdScore}>{row.tournaments[t.number] ?? '—'}</td>)}
                <td className={styles.tdTotal}>{row.overall}</td>
              </tr>)}</tbody>
            </table>
          </div>
        )}

        {rounds && rounds.length > 0 && (
          <div className={styles.weeklySection}>
            <h2 className={styles.weeklyTitle}>Weekly Results</h2>
            <div className={styles.weeklyGrid}>
              {[...rounds].reverse().map(r => (
                <Link key={r.id} to={`/round/${r.id}`} className={styles.weeklyCard}>
                  <span className={styles.weeklyLabel}>{roundLabel(r)}</span>
                  <span className={styles.weeklyCourse}>{courseLabel(r)}</span>
                  {r.leader_name ? (
                    <span className={styles.weeklyLeader}>
                      <span className={styles.weeklyLeaderName}>{r.leader_name}</span>
                      <span className={styles.weeklyLeaderScore}>{r.leader_net}</span>
                    </span>
                  ) : (
                    <span className={styles.weeklyNoScores}>No scores yet</span>
                  )}
                </Link>
              ))}
            </div>
          </div>
        )}
      </>}
    </div>
  )
}
