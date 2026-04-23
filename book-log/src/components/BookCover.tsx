import type { CSSProperties } from 'react'
import type { Book } from '../lib/types'
import { bookColor, bookPattern, type BookPattern } from '../lib/colors'

export type BookCoverSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl'

interface BookCoverProps {
  book: Pick<Book, 'id' | 'title' | 'authors' | 'coverUrl'>
  size?: BookCoverSize
  tilt?: number
  className?: string
}

const SIZES: Record<
  BookCoverSize,
  { w: number; h: number; titleSize: number; authorSize: number }
> = {
  xs: { w: 48, h: 70, titleSize: 8, authorSize: 6 },
  sm: { w: 68, h: 98, titleSize: 10, authorSize: 7 },
  md: { w: 92, h: 132, titleSize: 12, authorSize: 8 },
  lg: { w: 128, h: 184, titleSize: 15, authorSize: 10 },
  xl: { w: 156, h: 224, titleSize: 18, authorSize: 11 },
}

const PATTERN_BG: Record<BookPattern, string> = {
  stripes:
    'repeating-linear-gradient(135deg, rgba(255,255,255,0.13) 0, rgba(255,255,255,0.13) 4px, transparent 4px, transparent 10px)',
  dots: 'radial-gradient(circle at 3px 3px, rgba(255,255,255,0.18) 1.5px, transparent 2px)',
  scales:
    'radial-gradient(circle at 0 50%, transparent 8px, rgba(255,255,255,0.12) 8.5px, transparent 9.5px), radial-gradient(circle at 10px 50%, transparent 8px, rgba(255,255,255,0.12) 8.5px, transparent 9.5px)',
  grid:
    'linear-gradient(rgba(255,255,255,0.12) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.12) 1px, transparent 1px)',
  stars: 'radial-gradient(circle at 6px 6px, rgba(255,255,255,0.2) 1px, transparent 2px)',
}

const PATTERN_SIZE: Record<BookPattern, string> = {
  stripes: 'auto',
  dots: '12px 12px',
  scales: '20px 14px',
  grid: '14px 14px',
  stars: '18px 18px',
}

export default function BookCover({
  book,
  size = 'md',
  tilt,
  className = '',
}: BookCoverProps) {
  const s = SIZES[size]
  const transform = tilt ? `rotate(${tilt}deg)` : undefined

  if (book.coverUrl) {
    const style: CSSProperties = {
      width: s.w,
      height: s.h,
      transform,
      backgroundColor: 'var(--bg-2)',
    }
    return (
      <div className={`book-cover has-image ${className}`} style={style}>
        <img
          src={book.coverUrl}
          alt={`Cover of ${book.title}`}
          loading="lazy"
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      </div>
    )
  }

  const color = bookColor(book.id)
  const pattern = bookPattern(book.id)
  const style: CSSProperties = {
    width: s.w,
    height: s.h,
    background: color,
    backgroundImage: PATTERN_BG[pattern],
    backgroundSize: PATTERN_SIZE[pattern],
    transform,
  }

  return (
    <div className={`book-cover ${className}`} style={style}>
      <div
        style={{
          fontSize: s.titleSize,
          fontWeight: 700,
          lineHeight: 1.15,
          letterSpacing: '-0.01em',
          textShadow: '1px 1px 0 rgba(0,0,0,0.15)',
        }}
      >
        {book.title}
      </div>
      <div
        style={{
          fontSize: s.authorSize,
          fontFamily: 'Nunito, sans-serif',
          opacity: 0.85,
          fontWeight: 700,
          letterSpacing: '0.05em',
          textTransform: 'uppercase',
        }}
      >
        {book.authors[0] ?? ''}
      </div>
    </div>
  )
}
