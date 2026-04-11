import { useEffect, useState } from 'react'
import { api, type HandicapSeason } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './HandicapPage.module.css'

export function HandicapPage() {
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [data, setData] = useState<HandicapSeason[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!seasonId) return
    setLoading(true)
    api.get<HandicapSeason[]>(`/seasons/${seasonId}/handicaps`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [seasonId])

  return (
    <div>
      <h1 className={styles.title}>Handicaps</h1>
      <SeasonSelect value={seasonId} onChange={setSeasonId} />

      {loading && <p className={styles.loading}>Loading…</p>}

      {data.map(block => (
        <div key={block.tournament.id} className={styles.block}>
          <h2 className={styles.tournamentTitle}>{block.tournament.name}</h2>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Player</th>
                <th>Handicap</th>
              </tr>
            </thead>
            <tbody>
              {block.handicaps.map(row => (
                <tr key={row.player.id}>
                  <td>{row.player.name}</td>
                  <td className={styles.tdHcp}>
                    {row.handicap !== null ? row.handicap.toFixed(1) : <em className={styles.muted}>not set</em>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  )
}
