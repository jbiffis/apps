import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Hole, type Round, type Player } from '../api'
import styles from './AdminScoreEntryPage.module.css'

interface ScoreGrid {
  [playerId: number]: {
    [hole: number]: string
  }
}

export function AdminScoreEntryPage() {
  const { id } = useParams<{ id: string }>()
  const [round, setRound] = useState<Round | null>(null)
  const [holes, setHoles] = useState<Hole[]>([])
  const [players, setPlayers] = useState<Player[]>([])
  const [grid, setGrid] = useState<ScoreGrid>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState('')
  const inputRefs = useRef<Record<string, HTMLInputElement | null>>({})

  useEffect(() => {
    if (!id) return
    Promise.all([
      api.get<{ round: Round; holes: Hole[]; players: Array<{ player_id: number; name: string; holes: Record<string, number | null> }> }>(`/rounds/${id}`),
      api.get<Player[]>('/players'),
    ]).then(([sc, allPlayers]) => {
      setRound(sc.round)
      setHoles(sc.holes)
      // Use players from scorecard (sorted), fall back to all players
      const activePlayers = sc.players.length > 0
        ? sc.players.map(p => ({ id: p.player_id, name: p.name }))
        : allPlayers

      setPlayers(activePlayers)

      // Pre-fill grid with existing scores
      const initial: ScoreGrid = {}
      activePlayers.forEach(p => {
        initial[p.id] = {}
        const scorecardPlayer = sc.players.find(sp => sp.player_id === p.id)
        sc.holes.forEach(h => {
          const existing = scorecardPlayer?.holes?.[h.hole_number]
          initial[p.id][h.hole_number] = existing != null ? String(existing) : ''
        })
      })
      setGrid(initial)
    }).finally(() => setLoading(false))
  }, [id])

  function cellKey(playerId: number, hole: number) {
    return `${playerId}-${hole}`
  }

  function handleChange(playerId: number, hole: number, val: string) {
    setGrid(prev => ({
      ...prev,
      [playerId]: { ...prev[playerId], [hole]: val },
    }))
    setSaved(false)
  }

  function handleKeyDown(e: React.KeyboardEvent, playerId: number, hole: number) {
    if (e.key === 'Enter' || e.key === 'Tab') return // let tab handle focus
    if (e.key === 'ArrowRight') {
      e.preventDefault()
      const nextHole = hole + 1
      if (nextHole <= holes.length) {
        inputRefs.current[cellKey(playerId, nextHole)]?.focus()
      } else {
        // Move to next player, hole 1
        const idx = players.findIndex(p => p.id === playerId)
        if (idx < players.length - 1) {
          inputRefs.current[cellKey(players[idx + 1].id, holes[0].hole_number)]?.focus()
        }
      }
    }
    if (e.key === 'ArrowLeft') {
      e.preventDefault()
      const prevHole = hole - 1
      if (prevHole >= holes[0].hole_number) {
        inputRefs.current[cellKey(playerId, prevHole)]?.focus()
      }
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      const idx = players.findIndex(p => p.id === playerId)
      if (idx < players.length - 1) {
        inputRefs.current[cellKey(players[idx + 1].id, hole)]?.focus()
      }
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      const idx = players.findIndex(p => p.id === playerId)
      if (idx > 0) {
        inputRefs.current[cellKey(players[idx - 1].id, hole)]?.focus()
      }
    }
  }

  // Auto-advance to next hole after 2-digit entry or valid single digit
  function handleInput(e: React.FormEvent<HTMLInputElement>, playerId: number, hole: number) {
    const val = (e.target as HTMLInputElement).value
    if (val.length >= 2) {
      const nextHole = hole + 1
      if (nextHole <= holes.length) {
        inputRefs.current[cellKey(playerId, nextHole)]?.focus()
        inputRefs.current[cellKey(playerId, nextHole)]?.select()
      } else {
        const idx = players.findIndex(p => p.id === playerId)
        if (idx < players.length - 1) {
          const firstHole = holes[0].hole_number
          inputRefs.current[cellKey(players[idx + 1].id, firstHole)]?.focus()
          inputRefs.current[cellKey(players[idx + 1].id, firstHole)]?.select()
        }
      }
    }
  }

  async function saveScores() {
    if (!id) return
    setSaving(true)
    setError('')
    try {
      const scores = players.map(player => ({
        player_id: player.id,
        holes: holes.map(h => ({
          hole_number: h.hole_number,
          strokes: grid[player.id]?.[h.hole_number]
            ? Number(grid[player.id][h.hole_number])
            : null,
        })).filter(h => h.strokes != null),
      })).filter(p => p.holes.length > 0)

      await api.post(`/rounds/${id}/scores/batch`, { scores })
      setSaved(true)

      // Reload to show fresh data
      const sc = await api.get<{ players: Array<{ player_id: number; name: string; holes: Record<string, number | null> }> }>(`/rounds/${id}`)
      const updated: ScoreGrid = {}
      players.forEach(p => {
        updated[p.id] = {}
        const sp = sc.players.find(x => x.player_id === p.id)
        holes.forEach(h => {
          const v = sp?.holes?.[h.hole_number]
          updated[p.id][h.hole_number] = v != null ? String(v) : ''
        })
      })
      setGrid(updated)
    } catch (e) {
      setError('Failed to save scores. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className={styles.loading}>Loading…</p>
  if (!round) return <p>Round not found.</p>

  const parTotal = holes.reduce((s, h) => s + h.par, 0)

  function playerTotal(playerId: number) {
    const vals = holes.map(h => grid[playerId]?.[h.hole_number]).filter(Boolean).map(Number)
    if (vals.length === 0) return null
    return vals.reduce((a, b) => a + b, 0)
  }

  return (
    <div className={styles.page}>
      <div className={styles.breadcrumb}>
        <Link to="/admin">Admin</Link>
        {' / '}Score Entry
      </div>

      <div className={styles.headerRow}>
        <div>
          <h1 className={styles.title}>Enter Scores</h1>
          <p className={styles.subtitle}>
            {round.is_practice ? 'Practice ' : ''}Round {round.round_number} — {round.course_name} · {round.nine} nine · {round.played_date}
          </p>
        </div>
        <div className={styles.saveArea}>
          {error && <span className={styles.error}>{error}</span>}
          {saved && <span className={styles.savedMsg}>Saved!</span>}
          <button
            className={styles.btnSave}
            onClick={saveScores}
            disabled={saving}
          >
            {saving ? 'Saving…' : 'Save All Scores'}
          </button>
        </div>
      </div>

      <p className={styles.hint}>Use arrow keys or Tab to navigate between cells. Scores auto-advance after 2 digits.</p>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th className={styles.thName}>Player</th>
              {holes.map(h => (
                <th key={h.hole_number} className={styles.thHole}>
                  <span className={styles.holeNum}>H{h.hole_number}</span>
                  <span className={styles.holePar}>p{h.par}</span>
                </th>
              ))}
              <th className={styles.thTotal}>Total</th>
            </tr>
          </thead>
          <tbody>
            <tr className={styles.parRow}>
              <td className={styles.tdParLabel}>Par</td>
              {holes.map(h => (
                <td key={h.hole_number} className={styles.tdPar}>{h.par}</td>
              ))}
              <td className={styles.tdPar}>{parTotal}</td>
            </tr>
            {players.map((player, pi) => {
              const total = playerTotal(player.id)
              const totalDiff = total != null ? total - parTotal : null
              return (
                <tr key={player.id} className={pi % 2 === 1 ? styles.rowAlt : ''}>
                  <td className={styles.tdName}>{player.name}</td>
                  {holes.map(h => {
                    const val = grid[player.id]?.[h.hole_number] ?? ''
                    const num = Number(val)
                    const diff = val && num > 0 ? num - h.par : null
                    let cellClass = styles.tdCell
                    if (diff !== null) {
                      if (diff <= -2) cellClass = `${styles.tdCell} ${styles.eagle}`
                      else if (diff === -1) cellClass = `${styles.tdCell} ${styles.birdie}`
                      else if (diff === 0) cellClass = `${styles.tdCell} ${styles.par}`
                      else if (diff === 1) cellClass = `${styles.tdCell} ${styles.bogey}`
                      else if (diff === 2) cellClass = `${styles.tdCell} ${styles.double}`
                      else cellClass = `${styles.tdCell} ${styles.worse}`
                    }
                    return (
                      <td key={h.hole_number} className={cellClass}>
                        <input
                          ref={el => { inputRefs.current[cellKey(player.id, h.hole_number)] = el }}
                          type="number"
                          inputMode="numeric"
                          min={1}
                          max={15}
                          className={styles.scoreInput}
                          value={val}
                          onChange={e => handleChange(player.id, h.hole_number, e.target.value)}
                          onKeyDown={e => handleKeyDown(e, player.id, h.hole_number)}
                          onInput={e => handleInput(e, player.id, h.hole_number)}
                          onFocus={e => e.target.select()}
                          placeholder="—"
                        />
                      </td>
                    )
                  })}
                  <td className={styles.tdTotal}>
                    {total != null ? (
                      <>
                        <span className={styles.totalGross}>{total}</span>
                        <span className={totalDiff != null && totalDiff > 0
                          ? styles.totalOver
                          : totalDiff != null && totalDiff < 0
                          ? styles.totalUnder
                          : styles.totalEven}>
                          {totalDiff != null && totalDiff > 0 ? `+${totalDiff}` : totalDiff}
                        </span>
                      </>
                    ) : '—'}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <div className={styles.bottomSave}>
        <button
          className={styles.btnSave}
          onClick={saveScores}
          disabled={saving}
        >
          {saving ? 'Saving…' : 'Save All Scores'}
        </button>
        <Link className={styles.btnViewRound} to={`/round/${id}`}>
          View Scorecard
        </Link>
        <Link className={styles.btnLive} to={`/in-round/${id}`}>
          Live Round (CTP / Chip-in)
        </Link>
      </div>
    </div>
  )
}
