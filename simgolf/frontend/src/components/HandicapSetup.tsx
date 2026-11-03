import { useEffect, useState } from 'react'
import { api, type HandicapSeason } from '../api'
import styles from '../pages/AdminPage.module.css'

interface Calculation {
  updated: Array<{ player_id: number; name: string; handicap: number }>
  skipped: Array<{ player_id: number; name: string; eligible_rounds: number; required_rounds: number }>
}

export function HandicapSetup({ seasonId, scoredTournaments, disabled }: {
  seasonId: number
  scoredTournaments: number[]
  disabled: boolean
}) {
  const [data, setData] = useState<HandicapSeason[]>([])
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [skipped, setSkipped] = useState<Calculation['skipped']>([])

  useEffect(() => {
    let active = true
    api.get<HandicapSeason[]>(`/seasons/${seasonId}/handicaps`)
      .then(result => { if (active) setData(result) })
      .catch(() => { if (active) setError('Unable to load handicaps.') })
    return () => { active = false }
  }, [seasonId])

  async function calculate(number: number) {
    if (busy !== null) return
    setBusy(number)
    setError('')
    setMessage('')
    setSkipped([])
    try {
      const result = await api.post<Calculation>(`/seasons/${seasonId}/recalculate-handicaps/${number}`, {})
      setSkipped(result.skipped)
      setMessage(result.updated.length > 0
        ? `Tournament ${number}: calculated handicaps for ${result.updated.length} player(s).`
        : 'No handicaps calculated. Players need enough complete rounds first.')
      setData(await api.get<HandicapSeason[]>(`/seasons/${seasonId}/handicaps`))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to calculate handicaps.')
    } finally {
      setBusy(null)
    }
  }

  const players = data[0]?.handicaps ?? []
  return (
    <section className={styles.formCard} aria-label="Season handicaps">
      <h2 className={styles.formTitle}>Handicaps — Best Rounds</h2>
      <p>T1: combined score minus combined par from both practice rounds. T2: average of the best 6 of 8 rounds (practice + T1). T3: average of the best 10 of 14 rounds (practice + T1 + T2).</p>
      <p className={styles.hint}>Best means lowest score relative to par. Handicaps use one decimal, with no adjustment limits or per-hole score caps. Incomplete rounds are excluded; a player needs 2, 6 or 10 complete rounds respectively. Calculate before entering that tournament’s scores; handicaps then stay fixed.</p>
      <div className={styles.controls}>
        {[1, 2, 3].map(number => {
          const locked = scoredTournaments.includes(number)
          return <button key={number} className={styles.btnOutline}
            disabled={disabled || busy !== null || locked}
            title={locked ? 'Handicaps are locked because this tournament has scores.' : undefined}
            onClick={() => void calculate(number)}>
            {busy === number ? 'Calculating…' : locked ? `T${number} Handicaps Locked` : `Calculate T${number} Handicaps`}
          </button>
        })}
      </div>
      {error && <p className={styles.errorMsg} role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
      {skipped.length > 0 && <ul>{skipped.map(player => (
        <li key={player.player_id}>{player.name}: {player.eligible_rounds} complete rounds, {player.required_rounds} needed. Existing handicap unchanged.</li>
      ))}</ul>}
      {players.length > 0 && <div className="table-scroll">
        <table className={`${styles.table} ${styles.handicapTable}`}>
          <caption className={styles.hint}>Stored handicaps for this season</caption>
          <thead><tr><th>Player</th><th>T1</th><th>T2</th><th>T3</th></tr></thead>
          <tbody>{players.map(({ player }) => <tr key={player.id}>
            <td className={styles.tdCourse}>{player.name}</td>
            {[1, 2, 3].map(number => {
              const value = data.find(block => block.tournament.number === number)?.handicaps.find(row => row.player.id === player.id)?.handicap
              return <td key={number} className={styles.tdRound}>{value == null ? 'Not set' : value.toFixed(1)}</td>
            })}
          </tr>)}</tbody>
        </table>
      </div>}
    </section>
  )
}
