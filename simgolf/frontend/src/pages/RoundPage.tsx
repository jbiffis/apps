import { useEffect, useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { api, type Scorecard, type ScoreEdit } from '../api'
import { GolfScore } from '../components/GolfScore'
import styles from './RoundPage.module.css'

export function RoundPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [data, setData] = useState<Scorecard | null>(null)
  const [loading, setLoading] = useState(true)
  const [editMode, setEditMode] = useState(false)
  const [confirmEdit, setConfirmEdit] = useState(false)
  const [editedScores, setEditedScores] = useState<Record<string, Record<number, string>>>({})
  const [ctpEditing, setCtpEditing] = useState(false)
  const [ctpHole, setCtpHole] = useState<string>('')
  const [ctpYardage, setCtpYardage] = useState<string>('')
  const [ctpPrize, setCtpPrize] = useState<string>('')

  useEffect(() => {
    if (!id) return
    api.get<Scorecard>(`/rounds/${id}`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <p className={styles.loading}>Loading…</p>
  if (!data) return <p>Round not found.</p>

  async function deleteRound() {
    if (!id || !data) return
    if (!window.confirm(`Delete Round ${data.round.round_number} (${data.round.course_name} · ${data.round.nine})? This will delete all scores, CTP entries, and edit history for this round.`)) return
    await api.del(`/rounds/${id}`)
    if (data.round.tournament_id) {
      navigate(`/tournament/${data.round.tournament_id}`)
    } else {
      navigate('/')
    }
  }

  const { round, holes, players, ctp_entries, edits } = data

  function openCtpEdit() {
    setCtpHole(String(round.ctp_hole ?? ''))
    setCtpYardage(String(round.ctp_yardage ?? ''))
    setCtpPrize(String(round.ctp_prize_amount ?? '20'))
    setCtpEditing(true)
  }

  async function saveCtpSettings() {
    if (!id) return
    await api.put(`/rounds/${id}`, {
      ctp_hole: ctpHole ? Number(ctpHole) : null,
      ctp_yardage: ctpYardage ? Number(ctpYardage) : null,
      ctp_prize_amount: ctpPrize ? Number(ctpPrize) : 20,
    })
    const fresh = await api.get<Scorecard>(`/rounds/${id}`)
    setData(fresh)
    setCtpEditing(false)
  }

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
          <div className={styles.editActions}>
            <button className={styles.editBtn} onClick={() => setConfirmEdit(true)}>Edit Scores</button>
            <button className={styles.deleteBtn} onClick={deleteRound}>Delete Round</button>
          </div>
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
      <div className={styles.section}>
        <div className={styles.ctpHeader}>
          <h2 className={styles.sectionTitle}>Closest to the Pin</h2>
          {!ctpEditing && (
            <button className={styles.ctpEditBtn} onClick={openCtpEdit}>
              {round.ctp_hole ? 'Edit Settings' : 'Set Up CTP'}
            </button>
          )}
        </div>

        {ctpEditing && (
          <div className={styles.ctpSettingsForm}>
            <label className={styles.ctpLabel}>
              Hole
              <select
                className={styles.ctpSelect}
                value={ctpHole}
                onChange={e => setCtpHole(e.target.value)}
              >
                <option value="">— none —</option>
                {holes.map(h => (
                  <option key={h.hole_number} value={h.hole_number}>
                    Hole {h.hole_number} (Par {h.par})
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.ctpLabel}>
              Yardage
              <input
                type="number"
                className={styles.ctpInput}
                value={ctpYardage}
                onChange={e => setCtpYardage(e.target.value)}
                placeholder="e.g. 140"
              />
            </label>
            <label className={styles.ctpLabel}>
              Prize ($)
              <input
                type="number"
                className={styles.ctpInput}
                value={ctpPrize}
                onChange={e => setCtpPrize(e.target.value)}
                placeholder="20"
              />
            </label>
            <div className={styles.ctpFormActions}>
              <button className={styles.btnPrimary} onClick={saveCtpSettings}>Save</button>
              <button className={styles.btnSecondary} onClick={() => setCtpEditing(false)}>Cancel</button>
            </div>
          </div>
        )}

        {round.ctp_hole && !ctpEditing && (
          <p className={styles.ctpInfo}>
            Hole {round.ctp_hole}{round.ctp_yardage ? ` — ${round.ctp_yardage} yards` : ''} · Prize: ${Number(round.ctp_prize_amount).toFixed(2)}
          </p>
        )}

        {(() => {
          const winner = ctp_entries.find(e => e.won)
          return winner ? (
            <div className={styles.ctpWinnerBanner}>
              🏆 <strong>{winner.player_name}</strong> won CTP
              {winner.distance_feet != null ? ` — ${winner.distance_feet} ft` : ''}
            </div>
          ) : null
        })()}

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
                <tr key={e.id} className={e.won ? styles.ctpWinnerRow : ''}>
                  <td>{e.player_name}</td>
                  <td>{e.distance_feet != null ? `${e.distance_feet} ft` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : !ctpEditing && (
          <p className={styles.muted}>No CTP entries yet.</p>
        )}
      </div>

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
