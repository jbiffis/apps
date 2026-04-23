import type { Book } from './types'

export interface LibraryStats {
  total: number
  reading: number
  tbr: number
  finished: number
  finishedThisYear: number
  totalPagesRead: number
  avgRating: number | null
  topAuthors: { name: string; count: number }[]
  finishedByYear: { year: number; count: number }[]
}

export interface BadgeRule {
  id: string
  name: string
  desc: string
  emoji: string
  colorVar: string
  /** returns 0..1 for progress. Unlocked when >= 1. */
  check: (s: LibraryStats, books: Book[]) => { progress: number; label?: string }
}

export const YEAR_GOAL = 20

export function computeStats(books: Book[]): LibraryStats {
  const now = new Date()
  const year = now.getFullYear()
  let reading = 0
  let tbr = 0
  let finished = 0
  let finishedThisYear = 0
  let totalPagesRead = 0
  let ratedCount = 0
  let ratingSum = 0
  const authorCount: Record<string, number> = {}
  const yearMap: Record<number, number> = {}

  for (const b of books) {
    if (b.status === 'reading') reading++
    else if (b.status === 'want-to-read') tbr++
    else if (b.status === 'read') {
      finished++
      if (b.pageCount) totalPagesRead += b.pageCount
      if (b.dateFinished) {
        const y = new Date(b.dateFinished).getFullYear()
        yearMap[y] = (yearMap[y] ?? 0) + 1
        if (y === year) finishedThisYear++
      }
    }
    for (const a of b.authors) {
      authorCount[a] = (authorCount[a] ?? 0) + 1
    }
    if (b.rating) {
      ratedCount++
      ratingSum += b.rating
    }
  }

  const topAuthors = Object.entries(authorCount)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)

  const finishedByYear = Object.entries(yearMap)
    .map(([y, n]) => ({ year: Number(y), count: n }))
    .sort((a, b) => b.year - a.year)

  return {
    total: books.length,
    reading,
    tbr,
    finished,
    finishedThisYear,
    totalPagesRead,
    avgRating: ratedCount > 0 ? ratingSum / ratedCount : null,
    topAuthors,
    finishedByYear,
  }
}

export const BADGE_RULES: BadgeRule[] = [
  {
    id: 'first-page',
    name: 'First Page',
    desc: 'Logged your very first book',
    emoji: '🌱',
    colorVar: 'var(--sticker-green)',
    check: (_s, books) => ({ progress: books.length >= 1 ? 1 : 0 }),
  },
  {
    id: 'five-book-club',
    name: 'Five-Book Club',
    desc: 'Finished 5 whole books',
    emoji: '🖐️',
    colorVar: 'var(--sticker-yellow)',
    check: (s) => ({ progress: Math.min(1, s.finished / 5), label: `${s.finished}/5` }),
  },
  {
    id: 'bookworm',
    name: 'Bookworm',
    desc: 'Read 10 books in total',
    emoji: '🐛',
    colorVar: 'var(--sticker-green)',
    check: (s) => ({ progress: Math.min(1, s.finished / 10), label: `${s.finished}/10` }),
  },
  {
    id: 'review-writer',
    name: 'Review Writer',
    desc: 'Wrote your first book review',
    emoji: '✏️',
    colorVar: 'var(--sticker-yellow)',
    check: (_s, books) => ({
      progress: books.some((b) => b.notes && b.notes.trim().length > 0) ? 1 : 0,
    }),
  },
  {
    id: 'dragon-tamer',
    name: 'Dragon Tamer',
    desc: 'Finished a 300+ page book',
    emoji: '🐉',
    colorVar: 'var(--sticker-pink)',
    check: (_s, books) => ({
      progress: books.some(
        (b) => b.status === 'read' && (b.pageCount ?? 0) >= 300,
      )
        ? 1
        : 0,
    }),
  },
  {
    id: 'genre-hopper',
    name: 'Genre Hopper',
    desc: 'Finished books with 3 different tags',
    emoji: '🌀',
    colorVar: 'var(--sticker-pink)',
    check: (_s, books) => {
      const tags = new Set<string>()
      for (const b of books) {
        if (b.status !== 'read') continue
        for (const t of b.tags) tags.add(t)
      }
      return {
        progress: Math.min(1, tags.size / 3),
        label: `${tags.size}/3 tags`,
      }
    },
  },
  {
    id: 'ten-derfoot',
    name: 'Ten-derfoot',
    desc: 'Finish 10 books this year',
    emoji: '🔟',
    colorVar: 'var(--sticker-blue)',
    check: (s) => ({
      progress: Math.min(1, s.finishedThisYear / 10),
      label: `${s.finishedThisYear}/10`,
    }),
  },
  {
    id: 'library-legend',
    name: 'Library Legend',
    desc: 'Add 20 books to your TBR',
    emoji: '📚',
    colorVar: 'var(--sticker-green)',
    check: (s) => ({ progress: Math.min(1, s.tbr / 20), label: `${s.tbr}/20` }),
  },
  {
    id: 'critic-mode',
    name: 'Critic Mode',
    desc: 'Write 10 reviews',
    emoji: '🎯',
    colorVar: 'var(--sticker-blue)',
    check: (_s, books) => {
      const n = books.filter((b) => b.notes && b.notes.trim().length > 0).length
      return { progress: Math.min(1, n / 10), label: `${n}/10` }
    },
  },
  {
    id: 'quest-champion',
    name: 'Quest Champion',
    desc: `Finish ${YEAR_GOAL} books this year`,
    emoji: '🏆',
    colorVar: 'var(--sticker-yellow)',
    check: (s) => ({
      progress: Math.min(1, s.finishedThisYear / YEAR_GOAL),
      label: `${s.finishedThisYear}/${YEAR_GOAL}`,
    }),
  },
]

export interface BadgeState {
  rule: BadgeRule
  unlocked: boolean
  progress: number
  label?: string
}

export function computeBadges(stats: LibraryStats, books: Book[]): BadgeState[] {
  return BADGE_RULES.map((rule) => {
    const { progress, label } = rule.check(stats, books)
    return { rule, progress, label, unlocked: progress >= 1 }
  })
}
