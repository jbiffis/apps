import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type PrizeSummary } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './PrizesPage.module.css'

export function PrizesPage() {
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [data, setData] = useState<PrizeSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const selectSeason = useCallback((id: number) => {
    setSeasonId(id)
    setData([])
    setError('')
    setLoading(true)
  }, [])

  useEffect(() => {
    if (!seasonId) return
    let active = true
    api.get<PrizeSummary[]>(`/seasons/${seasonId}/prizes`)
      .then(result => { if (active) setData(result) })
      .catch(() => { if (active) setError('Unable to load prizes. Please try again.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [seasonId])

  return (
    <div>
      <div className="page-heading">
        <div><p className="eyebrow">The clubhouse / The prize purse</p>
          <h1 className={styles.title}>Prize winnings</h1>
          <p className="page-description">Good rounds deserve a little recognition.</p>
        </div>
        <SeasonSelect value={seasonId} onChange={selectSeason} />
      </div>

      {loading && <p className={styles.loading}>Loading…</p>}
      {error && <p className="empty-panel" role="alert">{error}</p>}

      {data.length > 0 && (
        <>
        <div className={styles.purse}><span>Season winnings</span><strong>${data.reduce((total, row) => total + Number(row.total), 0).toFixed(2)}</strong><p>Total prizes awarded across the field</p></div>
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
        </>
      )}
      {!loading && !error && seasonId !== null && data.length === 0 && <p className="empty-panel">No prize winnings recorded for this season yet.</p>}
    </div>
  )
}
