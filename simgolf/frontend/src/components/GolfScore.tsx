interface GolfScoreProps {
  strokes: number
  par: number
}

/**
 * Renders a golf score with proper golf formatting:
 * eagle = double circle, birdie = circle, par = plain,
 * bogey = square, double bogey = double square, 3+ = solid square
 */
export function GolfScore({ strokes, par }: GolfScoreProps) {
  const diff = strokes - par

  if (diff <= -2) {
    return <span className="score-eagle">{strokes}</span>
  }
  if (diff === -1) {
    return <span className="score-birdie">{strokes}</span>
  }
  if (diff === 0) {
    return <span className="score-par">{strokes}</span>
  }
  if (diff === 1) {
    return <span className="score-bogey">{strokes}</span>
  }
  if (diff === 2) {
    return <span className="score-double-bogey">{strokes}</span>
  }
  return <span className="score-triple-plus">{strokes}</span>
}
