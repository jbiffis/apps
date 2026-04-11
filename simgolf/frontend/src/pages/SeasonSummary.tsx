import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type SeasonSummary, type Tournament } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './SeasonSummary.module.css'

export function SeasonSummaryPage() {
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [data, setData] = useState<SeasonSummary | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!seasonId) return
    setLoading(true)
    api.get<SeasonSummary>(`/seasons/${seasonId}/summary`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [seasonId])

  return (
    <div>
      <h1 className={styles.title}>Season Standings</h1>
      <SeasonSelect value={seasonId} onChange={setSeasonId} />

      {loading && <p className={styles.loading}>Loading…</p>}

      {data && (
        <div className="table-scroll">
          <table className={`${styles.table} table-sticky`}>
            <thead>
              <tr>
                <th className={styles.thPos}>Pos</th>
                <th className={styles.thName}>Player</th>
                {data.tournaments.map((t: Tournament) => (
                  <th key={t.id} className={styles.thTournament}>
                    <Link to={`/tournament/${t.id}`}>T{t.number}</Link>
                  </th>
                ))}
                <th className={styles.thTotal}>Total</th>
              </tr>
            </thead>
            <tbody>
              {data.players.map(row => (
                <tr key={row.player_id}>
                  <td className={styles.tdPos}>{row.position}</td>
                  <td className={styles.tdName}>
                    <Link to={`/player/${row.player_id}?season=${seasonId}`}>{row.name}</Link>
                  </td>
                  {data.tournaments.map((t: Tournament) => (
                    <td key={t.id} className={styles.tdScore}>
                      {row.tournaments[t.number] ?? '—'}
                    </td>
                  ))}
                  <td className={styles.tdTotal}>{row.overall}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
