import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type PrizeSummary } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './PrizesPage.module.css'

export function PrizesPage() {
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [data, setData] = useState<PrizeSummary[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!seasonId) return
    setLoading(true)
    api.get<PrizeSummary[]>(`/seasons/${seasonId}/prizes`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [seasonId])

  return (
    <div>
      <h1 className={styles.title}>Prize Winnings</h1>
      <SeasonSelect value={seasonId} onChange={setSeasonId} />

      {loading && <p className={styles.loading}>Loading…</p>}

      {data.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Player</th>
              <th className={styles.thAmount}>Prize Purse</th>
            </tr>
          </thead>
          <tbody>
            {data.map(row => (
              <tr key={row.id}>
                <td>
                  <Link to={`/player/${row.id}?season=${seasonId}#prizes`}>{row.name}</Link>
                </td>
                <td className={styles.tdAmount}>
                  ${Number(row.total).toFixed(2)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
