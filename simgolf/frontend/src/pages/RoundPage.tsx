import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Scorecard, type ScoreEdit } from '../api'
import { GolfScore } from '../components/GolfScore'
import styles from './RoundPage.module.css'

export function RoundPage() {
  const { id } = useParams<{ id: string }>()
  const [data, setData] = useState<Scorecard | null>(null)
  const [loading, setLoading] = useState(true)
  const [editMode, setEditMode] = useState(false)
  const [confirmEdit, setConfirmEdit] = useState(false)
  const [editedScores, setEditedScores] = useState<Record<string, Record<number, string>>>({})

  useEffect(() => {
    if (!id) return
    api.get<Scorecard>(`/rounds/${id}`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <p className={styles.loading}>Loading…</p>
  if (!data) return <p>Round not found.</p>

  const { round, holes, players, ctp_entries, edits } = data

  const parTotal = holes.reduce((sum, h) => sum + h.par, 0)

  function getEditedStroke(playerId: number, hole: number): string {
    return editedScores[playerId]?.[hole] ?? String(players.find(p => p.player_id === playerId)?.holes?.[hole] ?? '')
  }

  function handleScoreChange(playerId: number, hole: number, val: string) {
    setEditedScores(prev => ({
      ...prev,
      [playerId]: { ...prev[playerId], [hole]: val },
    }))
  }

  async function saveEdits() {
    if (!id) return
    for (const [pidStr, holeMap] of Object.entries(editedScores)) {
      const pid = Number(pidStr)
      for (const [holeStr, val] of Object.entries(holeMap)) {
        const strokes = val === '' ? null : Number(val)
        await api.put(`/rounds/${id}/scores`, {
          player_id: pid,
          hole_number: Number(holeStr),
          strokes,
          edited_by: 'admin',
        })
      }
    }
    // Reload
    const fresh = await api.get<Scorecard>(`/rounds/${id}`)
    setData(fresh)
    setEditMode(false)
    setEditedScores({})
  }

  return (
    <div>
      <div className={styles.breadcrumb}>
        {round.tournament_id ? (
          <Link to={`/tournament/${round.tournament_id}`}>Tournament</Link>
        ) : (
          <Link to="/">Season</Link>
        )}
        {' / '}
        Round {round.round_number} — {round.course_name} · {round.nine}
      </div>

      <div className={styles.headerRow}>
        <h1 className={styles.title}>
          {round.is_practice ? 'Practice ' : ''}Round {round.round_number}
          <span className={styles.subtitle}>{round.course_name} · {round.nine} nine · {round.played_date}</span>
        </h1>
        {!editMode && (
          <button className={styles.editBtn} onClick={() => setConfirmEdit(true)}>Edit Scores</button>
        )}
        {editMode && (
          <div className={styles.editActions}>
            <button className={styles.saveBtn} onClick={() => {
              if (window.confirm('Save all score changes?')) saveEdits()
            }}>Save</button>
            <button className={styles.cancelBtn} onClick={() => { setEditMode(false); setEditedScores({}) }}>Cancel</button>
          </div>
        )}
      </div>

      {confirmEdit && !editMode && (
        <div className={styles.confirmBar}>
          <p>Enter edit mode? You can modify raw scores for this round.</p>
          <button className={styles.btnPrimary} onClick={() => { setEditMode(true); setConfirmEdit(false) }}>Yes, Edit</button>
          <button className={styles.btnSecondary} onClick={() => setConfirmEdit(false)}>Cancel</button>
        </div>
      )}

      {/* Scorecard table */}
      <div className="table-scroll">
        <table className={`${styles.table} table-sticky`}>
          <thead>
            <tr>
              <th className={styles.thFirst}></th>
              {holes.map(h => (
                <th key={h.hole_number} className={styles.thHole}>H{h.hole_number}</th>
              ))}
              <th className={styles.thStat}>Gross</th>
              <th className={styles.thStat}>
                <Link to="/handicap">Hdcp</Link>
              </th>
              <th className={styles.thStat}>Net</th>
              <th className={styles.thStat}>Pts</th>
            </tr>
            <tr className={styles.yardageRow}>
              <td className={styles.rowLabel}>Yardage</td>
              {holes.map(h => <td key={h.hole_number} className={styles.tdHole}>{h.yardage}</td>)}
              <td colSpan={4} className={styles.tdHole}>{holes.reduce((s, h) => s + h.yardage, 0)}</td>
            </tr>
            <tr className={styles.parRow}>
              <td className={styles.rowLabel}>Par</td>
              {holes.map(h => <td key={h.hole_number} className={styles.tdHole}>{h.par}</td>)}
              <td colSpan={4} className={styles.tdHole}>{parTotal}</td>
            </tr>
          </thead>
          <tbody>
            {players.map(player => (
              <tr key={player.player_id} className={player.absent ? styles.absentRow : ''}>
                <td className={styles.tdName}>
                  <Link to={`/player/${player.player_id}`}>{player.name}</Link>
                </td>
                {holes.map(h => {
                  const strokes = player.holes?.[h.hole_number]
                  if (editMode) {
                    return (
                      <td key={h.hole_number} className={styles.tdHole}>
                        <input
                          type="number"
                          min={1}
                          max={15}
                          className={styles.scoreInput}
                          value={getEditedStroke(player.player_id, h.hole_number)}
                          onChange={e => handleScoreChange(player.player_id, h.hole_number, e.target.value)}
                        />
                      </td>
                    )
                  }
                  return (
                    <td key={h.hole_number} className={styles.tdHole}>
                      {strokes != null
                        ? <GolfScore strokes={strokes} par={h.par} />
                        : <span className={styles.absent}>—</span>
                      }
                    </td>
                  )
                })}
                <td className={styles.tdStat}>{player.gross ?? '—'}</td>
                <td className={styles.tdStat}>{player.handicap}</td>
                <td className={styles.tdStat}>{player.net != null ? (Number.isInteger(player.net) ? player.net : player.net.toFixed(1)) : '—'}</td>
                <td className={styles.tdPts}>{player.points ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Closest to pin */}
      {(ctp_entries.length > 0 || round.ctp_hole) && (
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Closest to the Pin</h2>
          {round.ctp_hole && (
            <p className={styles.ctpInfo}>Hole {round.ctp_hole} — {round.ctp_yardage} yards · Prize: ${round.ctp_prize_amount}</p>
          )}
          {ctp_entries.length > 0 ? (
            <table className={styles.ctpTable}>
              <thead>
                <tr>
                  <th>Player</th>
                  <th>Distance</th>
                </tr>
              </thead>
              <tbody>
                {ctp_entries.map(e => (
                  <tr key={e.id} className={e.won ? styles.ctpWinner : ''}>
                    <td>{e.player_name}{e.won ? ' 🏆' : ''}</td>
                    <td>{e.distance_feet} ft</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className={styles.muted}>No CTP entries yet.</p>
          )}
        </div>
      )}

      {/* Edit history */}
      {edits.length > 0 && (
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Score Edits</h2>
          <ul className={styles.editList}>
            {edits.map((e: ScoreEdit) => (
              <li key={e.id} className={styles.editItem}>
                {e.edited_at.slice(0, 10)} — {e.player_name} Hole {e.hole_number} edited from {e.old_strokes ?? '—'} to {e.new_strokes ?? '—'}
                {e.edited_by && <span className={styles.editedBy}> by {e.edited_by}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
