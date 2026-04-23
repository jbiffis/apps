import type { ReadingStatus } from './types'

export interface StatusMeta {
  /** Design-shelf id matching the BookStory mockups. */
  shelf: 'reading' | 'tbr' | 'finished'
  label: string
  shortLabel: string
  emoji: string
  colorVar: string
}

export const STATUS_META: Record<ReadingStatus, StatusMeta> = {
  reading: {
    shelf: 'reading',
    label: 'Reading right now',
    shortLabel: 'Reading',
    emoji: '📖',
    colorVar: 'var(--accent-1)',
  },
  'want-to-read': {
    shelf: 'tbr',
    label: "Can't wait to read",
    shortLabel: 'Up next',
    emoji: '🔖',
    colorVar: 'var(--accent-3)',
  },
  read: {
    shelf: 'finished',
    label: 'Done & dusted',
    shortLabel: 'Finished',
    emoji: '⭐',
    colorVar: 'var(--accent-2)',
  },
}

export const STATUS_ORDER: ReadingStatus[] = ['reading', 'want-to-read', 'read']

export function statusFromShelf(shelf: 'reading' | 'tbr' | 'finished'): ReadingStatus {
  if (shelf === 'tbr') return 'want-to-read'
  if (shelf === 'finished') return 'read'
  return 'reading'
}
