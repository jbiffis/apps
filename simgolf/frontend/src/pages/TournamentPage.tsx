import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type TournamentDetail } from '../api'
import styles from './TournamentPage.module.css'

export function TournamentPage() {
  const { id } = useParams<{ id: string }>()
  const [data, setData] = useState<TournamentDetail | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    api.get<TournamentDetail>(`/tournaments/${id}`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <p className={styles.loading}>Loading…</p>
  if (!data) return <p>Tournament not found.</p>

  const { tournament, rounds, players } = data

  return (
    <div>
      <div className={styles.breadcrumb}>
        <Link to="/">Season</Link> / Tournament {tournament.number}
      </div>
      <h1 className={styles.title}>Tournament {tournament.number}</h1>

      <div className="table-scroll">
        <table className={`${styles.table} table-sticky`}>
          <thead>
            <tr>
              <th className={styles.thPos}>Pos</th>
              <th className={styles.thName}>Player</th>
              {rounds.map(r => (
                <th key={r.id} colSpan={2} className={styles.thRound}>
                  <Link to={`/round/${r.id}`}>
                    R{r.round_number}<br/>
                    <small>{r.course_name} · {r.nine}</small>
                  </Link>
                </th>
              ))}
              <th className={styles.thTotal}>Total</th>
            </tr>
            <tr className={styles.subHeader}>
              <th colSpan={2} />
              {rounds.map(r => (
                <>
                  <th key={`${r.id}-score`} className={styles.thSub}>Net</th>
                  <th key={`${r.id}-pts`} className={styles.thSub}>Pts</th>
                </>
              ))}
              <th />
            </tr>
          </thead>
          <tbody>
            {players.map((row, idx) => {
              const pos = idx + 1
              return (
                <tr key={row.player_id}>
                  <td className={styles.tdPos}>{pos}</td>
                  <td className={styles.tdName}>
                    <Link to={`/player/${row.player_id}`}>{row.name}</Link>
                  </td>
                  {rounds.map(r => {
                    const rData = row.rounds[r.id]
                    return rData ? (
                      <>
                        <td key={`${r.id}-n`} className={styles.tdScore}>
                          {rData.absent ? <em className={styles.absent}>—</em> : rData.net?.toFixed(0)}
                        </td>
                        <td key={`${r.id}-p`} className={styles.tdPts}>
                          {rData.points}
                        </td>
                      </>
                    ) : (
                      <>
                        <td key={`${r.id}-n`} className={styles.tdScore}>—</td>
                        <td key={`${r.id}-p`} className={styles.tdPts}>—</td>
                      </>
                    )
                  })}
                  <td className={styles.tdTotal}>{row.total}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
