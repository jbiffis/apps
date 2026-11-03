import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type SeasonSummary, type Tournament } from '../api'
import { SeasonSelect } from '../components/SeasonSelect'
import styles from './SeasonSummary.module.css'

function ArrowUpRight() {
  return <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m4 12 8-8M4 4h8v8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
}

export function SeasonSummaryPage() {
  const [seasonId, setSeasonId] = useState<number | null>(null)
  const [data, setData] = useState<SeasonSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const selectSeason = useCallback((id: number) => {
    setSeasonId(id)
    setData(null)
    setError('')
    setLoading(true)
  }, [])

  useEffect(() => {
    if (!seasonId) return
    let active = true
    api.get<SeasonSummary>(`/seasons/${seasonId}/summary`)
      .then(result => { if (active) setData(result) })
      .catch(() => { if (active) setError('Unable to load standings. Please try again.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [seasonId])

  return (
    <div>
      <div className={styles.heading}>
        <div>
          <p className="eyebrow">The clubhouse / Season overview</p>
          <h1 className={styles.title}>Season standings</h1>
          <p className="page-description">Every round counts. See how the field stacks up.</p>
        </div>
        <SeasonSelect value={seasonId} onChange={selectSeason} />
      </div>

      <section className={styles.banner} aria-label="Season overview">
        <div className={styles.bannerCopy}>
          <span className={styles.bannerLabel}>CREEKSIDE SIM GOLF</span>
          <h2>{data?.season?.name ?? 'A new season. The same great game.'}</h2>
          <p>Your league. Your rounds. Your place on the board.</p>
          <Link to="/admin">Manage season <ArrowUpRight /></Link>
        </div>
        <svg className={styles.courseArt} viewBox="0 0 420 240" fill="none" aria-hidden="true">
          <path d="M480 15C325-61 186-9 236 71S469 122 401 209 198 201 185 289" stroke="#42644e" strokeWidth="35" />
          <path d="M420-45C287-8 163 32 203 103S372 153 323 212 102 237 119 295" stroke="#587955" strokeWidth="1" />
          <path d="M450-13C307 12 206 38 243 103S418 152 367 223 158 261 159 308" stroke="#587955" strokeWidth="1" />
          <ellipse cx="278" cy="136" rx="60" ry="31" transform="rotate(-23 278 136)" fill="#c4da83" />
          <ellipse cx="297" cy="134" rx="7" ry="3" fill="#1c4234" />
          <path d="M297 134V65l32 11-32 13" stroke="#f1f2db" strokeWidth="2" strokeLinejoin="round" />
          <path d="m197 176-4 7M186 188l-4 6M175 202l-4 5" stroke="#a4ba8b" strokeWidth="2" strokeLinecap="round" />
          <circle cx="176" cy="213" r="4" fill="#f1f2db" />
        </svg>
      </section>

      {loading && <p className={styles.loading} role="status">Loading standings…</p>}
      {error && <p className="empty-panel" role="alert">{error}</p>}

      {data && <>
        <div className={styles.metrics}>
          <div><span>Players on the board</span><strong>{data.players.length.toString().padStart(2, '0')}</strong><small>In this season</small></div>
          <div><span>Tournaments</span><strong>{data.tournaments.length.toString().padStart(2, '0')}</strong><small>One overall leaderboard</small></div>
          <div><span>Leading points</span><strong>{data.players[0]?.overall ?? '—'}</strong><small>{data.players.length ? 'The score to catch' : 'Waiting for the first round'}</small></div>
        </div>
        <div className={styles.boardHeading}>
          <div><p className="eyebrow">The leaderboard</p><h2>Race to the top</h2></div>
          <span className={styles.pointsLabel}>Ranked by total points</span>
        </div>
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
      </>}
    </div>
  )
}
