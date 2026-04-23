import { useState } from 'react'
import Icon from './Icon'

interface StarRatingProps {
  rating?: 1 | 2 | 3 | 4 | 5
  size?: number
  onRate?: (rating: 1 | 2 | 3 | 4 | 5) => void
  interactive?: boolean
}

export default function StarRating({
  rating,
  size = 18,
  onRate,
  interactive = false,
}: StarRatingProps) {
  const [hover, setHover] = useState(0)
  return (
    <div style={{ display: 'inline-flex', gap: 2 }}>
      {[1, 2, 3, 4, 5].map((i) => {
        const active = (hover || rating || 0) >= i
        return (
          <button
            key={i}
            type="button"
            disabled={!interactive}
            aria-label={`${i} star${i > 1 ? 's' : ''}`}
            style={{
              cursor: interactive ? 'pointer' : 'default',
              lineHeight: 0,
              border: 'none',
              background: 'transparent',
              padding: 0,
            }}
            onClick={() => interactive && onRate?.(i as 1 | 2 | 3 | 4 | 5)}
            onMouseEnter={() => interactive && setHover(i)}
            onMouseLeave={() => interactive && setHover(0)}
          >
            <Icon
              name="star"
              size={size}
              fill={active ? 'var(--accent-2)' : 'transparent'}
              stroke="var(--line)"
              strokeWidth={2}
            />
          </button>
        )
      })}
    </div>
  )
}
