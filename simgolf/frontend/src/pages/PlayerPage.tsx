import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api, type PlayerDetail } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './PlayerPage.module.css'

export function PlayerPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const defaultSeason = searchParams.get('season') ? Number(searchParams.get('season')) : null
  const [seasonId, setSeasonId] = useState<number | null>(defaultSeason)
  const [data, setData] = useState<PlayerDetail | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!id || !seasonId) return
    setLoading(true)
    api.get<PlayerDetail>(`/players/${id}/seasons/${seasonId}`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [id, seasonId])

  return (
    <div>
      <div className={styles.breadcrumb}>
        <Link to="/">Season</Link> / Player
      </div>

      <SeasonSelect value={seasonId} onChange={setSeasonId} />

      {loading && <p className={styles.loading}>Loading…</p>}

      {data && (
        <>
          <h1 className={styles.title}>{data.player.name}</h1>

          {/* Practice Rounds */}
          {data.practice_rounds.length > 0 && (
            <div className={styles.tournamentBlock}>
              <h2 className={styles.tournamentTitle}>Practice Rounds</h2>
              <table className={styles.roundTable}>
                <thead>
                  <tr>
                    <th>Round</th>
                    <th className={styles.thPts}>Gross</th>
                  </tr>
                </thead>
                <tbody>
                  {data.practice_rounds.map(r => (
                    <tr key={r.round.id}>
                      <td>
                        <Link to={`/round/${r.round.id}`}>
                          R{r.round.round_number} — {r.round.course_name} · {r.round.nine}
                        </Link>
                      </td>
                      <td className={styles.tdPts}>
                        {r.scores?.absent ? '—' : r.scores?.gross ?? '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Tournaments */}
          {data.tournaments.map(t => (
            <div key={t.tournament.id} className={styles.tournamentBlock}>
              <h2 className={styles.tournamentTitle}>
                {t.tournament.name}
                {t.handicap != null && (
                  <span style={{ fontSize: '0.85rem', fontWeight: 400, marginLeft: '0.75rem', color: 'var(--color-muted)' }}>
                    HC: {t.handicap}
                  </span>
                )}
              </h2>
              <table className={styles.roundTable}>
                <thead>
                  <tr>
                    <th>Round</th>
                    <th className={styles.thPts}>Points</th>
                  </tr>
                </thead>
                <tbody>
                  {t.rounds.map(r => (
                    <tr key={r.round.id}>
                      <td>
                        <Link to={`/round/${r.round.id}`}>
                          R{r.round.round_number} — {r.round.course_name} · {r.round.nine}
                        </Link>
                      </td>
                      <td className={styles.tdPts}>{r.points}</td>
                    </tr>
                  ))}
                  <tr className={styles.totalRow}>
                    <td>Tournament {t.tournament.number} Total</td>
                    <td className={styles.tdPts}>{t.total}</td>
                  </tr>
                  {t.next_handicap != null && (
                    <tr className={styles.handicapRow}>
                      <td>New Handicap</td>
                      <td className={styles.tdHandicap}>{t.next_handicap}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          ))}

          {/* Overall */}
          {data.tournaments.length > 0 && (
            <div className={styles.overallTotal}>
              Overall Total: <strong>{data.overall_points}</strong> points
            </div>
          )}

          {/* Prize winnings */}
          <div className={styles.section}>
            <h2 className={styles.sectionTitle}>
              Prize Winnings
              {' '}
              <Link to={`/prizes?season=${seasonId}`} className={styles.prizesLink}>(View all)</Link>
            </h2>
            {data.prize_winnings.length === 0 ? (
              <p className={styles.muted}>No prize winnings yet.</p>
            ) : (
              <>
                <ul className={styles.prizeList}>
                  {data.prize_winnings.map(pw => (
                    <li key={pw.id} className={styles.prizeItem}>
                      {pw.description ?? `${pw.type} — $${pw.amount}`}
                      {' '}
                      <strong>${pw.amount}</strong>
                    </li>
                  ))}
                </ul>
                <p className={styles.prizeTotal}>
                  Total Prize Winnings:{' '}
                  <strong>${data.prize_winnings.reduce((s, pw) => s + Number(pw.amount), 0).toFixed(2)}</strong>
                </p>
              </>
            )}
          </div>
        </>
      )}
    </div>
  )
}
