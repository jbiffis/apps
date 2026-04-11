const BASE = '/api'

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`)
  if (!res.ok) throw new Error(`API error ${res.status}: ${path}`)
  return res.json()
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`API error ${res.status}: ${path}`)
  return res.json()
}

async function put<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`API error ${res.status}: ${path}`)
  return res.json()
}

export const api = { get, post, put }

// Types
export interface Season {
  id: number
  year: number
  name: string
}

export interface Player {
  id: number
  name: string
}

export interface Tournament {
  id: number
  season_id: number
  number: number
  name: string
}

export interface Round {
  id: number
  tournament_id: number | null
  season_id: number
  round_number: number
  course_id: number
  course_name?: string
  nine: 'front' | 'back'
  played_date: string
  is_practice: number
  ctp_hole: number | null
  ctp_yardage: number | null
  ctp_prize_amount: number
  chip_in_pot: number
}

export interface Hole {
  hole_number: number
  par: number
  yardage: number
}

export interface PlayerScoreRow {
  player_id: number
  name: string
  handicap: number
  holes: Record<string, number | null>
  gross: number | null
  net: number | null
  absent: boolean
  points: number
}

export interface Scorecard {
  round: Round
  holes: Hole[]
  players: PlayerScoreRow[]
  ctp_entries: CtpEntry[]
  edits: ScoreEdit[]
}

export interface CtpEntry {
  id: number
  round_id: number
  player_id: number
  player_name: string
  distance_feet: number
  won: number
}

export interface ScoreEdit {
  id: number
  round_id: number
  player_id: number
  player_name: string
  hole_number: number
  old_strokes: number | null
  new_strokes: number | null
  edited_by: string | null
  edited_at: string
}

export interface PrizeWinning {
  id: number
  player_id: number
  round_id: number | null
  tournament_id: number | null
  season_id: number
  type: 'ctp' | 'chip_in' | 't1st' | 't2nd'
  amount: number
  description: string | null
  awarded_at: string
  round_number?: number
  played_date?: string
  tournament_number?: number
  tournament_name?: string
}

export interface SeasonSummaryRow {
  player_id: number
  name: string
  tournaments: Record<number, number>
  overall: number
  position: number
}

export interface SeasonSummary {
  tournaments: Tournament[]
  players: SeasonSummaryRow[]
}

export interface TournamentDetail {
  tournament: Tournament & { year: number; season_name: string }
  rounds: Round[]
  players: Array<{
    player_id: number
    name: string
    rounds: Record<number, { net: number | null; points: number; absent: boolean }>
    total: number
  }>
}

export interface PlayerDetail {
  player: Player
  practice_rounds: Array<{ round: Round; scores: PlayerScoreRow }>
  tournaments: Array<{
    tournament: Tournament
    rounds: Array<{ round: Round; scores: PlayerScoreRow; points: number }>
    total: number
  }>
  prize_winnings: PrizeWinning[]
  overall_points: number
}

export interface HandicapSeason {
  tournament: Tournament
  handicaps: Array<{ player: Player; handicap: number | null }>
}

export interface PrizeSummary {
  id: number
  name: string
  total: number
}
