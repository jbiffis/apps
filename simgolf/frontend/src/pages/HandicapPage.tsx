import { useCallback, useEffect, useState } from 'react'
import { api, type HandicapSeason } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './HandicapPage.module.css'

export function HandicapPage() {
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [data, setData] = useState<HandicapSeason[]>([])
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
    api.get<HandicapSeason[]>(`/seasons/${seasonId}/handicaps`)
      .then(result => { if (active) setData(result) })
      .catch(() => { if (active) setError('Unable to load handicaps. Please try again.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [seasonId])

  return (
    <div>
      <div className="page-heading">
        <div><p className="eyebrow">The clubhouse / Player handicaps</p>
          <h1 className={styles.title}>Handicaps</h1>
          <p className="page-description">A level field, tournament by tournament.</p>
        </div>
        <SeasonSelect value={seasonId} onChange={selectSeason} />
      </div>

      {loading && <p className={styles.loading}>Loading…</p>}
      {error && <p className="empty-panel" role="alert">{error}</p>}

      <div className={styles.grid}>
      {data.map(block => (
        <div key={block.tournament.id} className={styles.block}>
          <h2 className={styles.tournamentTitle}>Tournament {block.tournament.number}</h2>
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
          {block.handicaps.length === 0 && <p className={styles.empty}>Handicaps will appear as players enter this season.</p>}
        </div>
      ))}
      </div>
    </div>
  )
}
