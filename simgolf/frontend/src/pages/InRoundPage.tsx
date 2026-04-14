import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Player, type Round } from '../api'
import styles from './InRoundPage.module.css'

interface CtpEntry { player_id: number; player_name: string; distance_feet: number; won: number }

export function InRoundPage() {
  const { id } = useParams<{ id: string }>()
  const [round, setRound] = useState<Round | null>(null)
  const [players, setPlayers] = useState<Player[]>([])
  const [ctpEntries, setCtpEntries] = useState<CtpEntry[]>([])
  const [loading, setLoading] = useState(true)

  // CTP form
  const [ctpPlayerId, setCtpPlayerId] = useState('')
  const [ctpDistance, setCtpDistance] = useState('')

  // Chip-in dialog
  const [chipInOpen, setChipInOpen] = useState(false)
  const [chipInPlayer, setChipInPlayer] = useState('')
  const [chipInAmount, setChipInAmount] = useState('')
  const [chipInMsg, setChipInMsg] = useState('')

  // CTP hole setup
  const [courseHoles, setCourseHoles] = useState<{ hole_number: number; par: number; yardage: number }[]>([])
  const [ctpHoleInput, setCtpHoleInput] = useState('')

  // CTP award state
  const [ctpFinalized, setCtpFinalized] = useState(false)
  const [ctpMsg, setCtpMsg] = useState('')

  useEffect(() => {
    if (!id) return
    Promise.all([
      api.get<{ round: Round; ctp_entries: CtpEntry[] }>(`/rounds/${id}`),
      api.get<Player[]>('/players'),
    ]).then(async ([scorecard, pList]) => {
      setRound(scorecard.round)
      setCtpEntries(scorecard.ctp_entries)
      setPlayers(pList)
      if (!scorecard.round.ctp_hole) {
        const courses = await api.get<{ id: number; holes: { hole_number: number; par: number; yardage: number }[] }[]>('/courses')
        const course = courses.find(c => c.id === scorecard.round.course_id)
        if (course) {
          const nine = scorecard.round.nine
          const holes = course.holes.filter(h =>
            nine === 'front' ? h.hole_number <= 9 : h.hole_number >= 10
          )
          setCourseHoles(holes)
        }
      }
    }).finally(() => setLoading(false))
  }, [id])

  async function addCtpEntry() {
    if (!id || !ctpPlayerId || !ctpDistance) return
    await api.post(`/rounds/${id}/ctp`, {
      player_id: Number(ctpPlayerId),
      distance_feet: Number(ctpDistance),
    })
    const sc = await api.get<{ ctp_entries: CtpEntry[] }>(`/rounds/${id}`)
    setCtpEntries(sc.ctp_entries)
    setCtpPlayerId('')
    setCtpDistance('')
  }

  async function setCtpHole() {
    if (!id || !ctpHoleInput) return
    const hole = courseHoles.find(h => h.hole_number === Number(ctpHoleInput))
    if (!hole) return
    const updated = await api.put<Round>(`/rounds/${id}`, {
      ctp_hole: hole.hole_number,
      ctp_yardage: hole.yardage,
    })
    setRound(updated)
  }

  async function awardCtp() {
    if (!id) return
    const result = await api.post<{ winner: CtpEntry | null; amount?: number; rolled_to?: number }>(`/rounds/${id}/ctp/award`, {})
    if (result.winner) {
      setCtpMsg(`${result.winner.player_name} wins $${result.amount} closest to pin!`)
    } else {
      setCtpMsg(`No one made the green — $20 rolls to next round.`)
    }
    setCtpFinalized(true)
    const sc = await api.get<{ ctp_entries: CtpEntry[] }>(`/rounds/${id}`)
    setCtpEntries(sc.ctp_entries)
  }

  async function submitChipIn() {
    if (!id || !chipInPlayer || !chipInAmount) return
    await api.post(`/rounds/${id}/chipin`, {
      player_id: Number(chipInPlayer),
      amount: Number(chipInAmount),
    })
    const p = players.find(pl => pl.id === Number(chipInPlayer))
    setChipInMsg(`${p?.name} won $${chipInAmount} chip-in pot!`)
    setChipInOpen(false)
    setChipInPlayer('')
    setChipInAmount('')
  }

  if (loading) return <p className={styles.loading}>Loading…</p>
  if (!round) return <p>Round not found.</p>

  return (
    <div>
      <div className={styles.breadcrumb}>
        {round.tournament_id
          ? <Link to={`/tournament/${round.tournament_id}`}>Tournament</Link>
          : <Link to="/">Season</Link>
        }
        {' / '}Round {round.round_number} — In Progress
      </div>

      <h1 className={styles.title}>
        Round {round.round_number} — Live Entry
        <span className={styles.subtitle}>{round.course_name} · {round.nine}</span>
      </h1>

      {/* CTP section */}
      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>Closest to the Pin</h2>
        {round.ctp_hole ? (
          <p className={styles.ctpInfo}>Hole {round.ctp_hole} — {round.ctp_yardage} yards · Prize: ${round.ctp_prize_amount}</p>
        ) : (
          <div className={styles.ctpForm}>
            <select
              className={styles.select}
              value={ctpHoleInput}
              onChange={e => setCtpHoleInput(e.target.value)}
            >
              <option value="">Select CTP hole…</option>
              {courseHoles.filter(h => h.par === 3).map(h => (
                <option key={h.hole_number} value={h.hole_number}>
                  Hole {h.hole_number} — Par 3, {h.yardage} yds
                </option>
              ))}
            </select>
            <button className={styles.btnAdd} onClick={setCtpHole} disabled={!ctpHoleInput}>
              Set CTP Hole
            </button>
          </div>
        )}

        {!ctpFinalized && (
          <>
            <div className={styles.ctpForm}>
              <select
                className={styles.select}
                value={ctpPlayerId}
                onChange={e => setCtpPlayerId(e.target.value)}
              >
                <option value="">Select player…</option>
                {players.map(p => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
              <input
                type="number"
                step="0.1"
                min="0"
                placeholder="Distance (ft)"
                className={styles.input}
                value={ctpDistance}
                onChange={e => setCtpDistance(e.target.value)}
              />
              <button className={styles.btnAdd} onClick={addCtpEntry} disabled={!ctpPlayerId || !ctpDistance}>
                Add
              </button>
            </div>

            {ctpEntries.length > 0 && (
              <table className={styles.ctpTable}>
                <thead>
                  <tr><th>Player</th><th>Distance</th></tr>
                </thead>
                <tbody>
                  {ctpEntries.map(e => (
                    <tr key={e.player_id}><td>{e.player_name}</td><td>{e.distance_feet} ft</td></tr>
                  ))}
                </tbody>
              </table>
            )}

            <button className={styles.btnFinalize} onClick={() => {
              if (ctpEntries.length === 0) {
                if (window.confirm('No golfers made the green — $20 moves to next week. Confirm?')) {
                  awardCtp()
                }
              } else {
                awardCtp()
              }
            }}>
              No More Golfers — Award CTP
            </button>
          </>
        )}

        {ctpMsg && <p className={styles.successMsg}>{ctpMsg}</p>}
      </div>

      {/* Chip-in section */}
      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>Chip In</h2>
        <button className={styles.btnChipIn} onClick={() => setChipInOpen(true)}>
          Golfer Chipped In!
        </button>
        {chipInMsg && <p className={styles.successMsg}>{chipInMsg}</p>}
      </div>

      {/* Chip-in dialog */}
      {chipInOpen && (
        <div className={styles.dialogOverlay}>
          <div className={styles.dialog}>
            <h3 className={styles.dialogTitle}>Record Chip In</h3>
            <div className={styles.dialogForm}>
              <select
                className={styles.select}
                value={chipInPlayer}
                onChange={e => setChipInPlayer(e.target.value)}
              >
                <option value="">Select player…</option>
                {players.map(p => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
              <label className={styles.dialogLabel}>Prize Winnings</label>
              <div className={styles.amountWrap}>
                <span className={styles.dollar}>$</span>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  className={styles.input}
                  value={chipInAmount}
                  onChange={e => setChipInAmount(e.target.value)}
                />
              </div>
            </div>
            <div className={styles.dialogActions}>
              <button className={styles.btnPrimary} onClick={submitChipIn} disabled={!chipInPlayer || !chipInAmount}>
                Confirm
              </button>
              <button className={styles.btnSecondary} onClick={() => setChipInOpen(false)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
