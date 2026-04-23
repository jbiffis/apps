import type { ReadingStatus } from './types'

export interface StatusMeta {
  emoji: string
  label: string
  cardBadge: string
  cardBorder: string
  chip: string
  chipActive: string
}

export const STATUS_META: Record<ReadingStatus, StatusMeta> = {
  'want-to-read': {
    emoji: '📚',
    label: 'On my list',
    cardBadge: 'bg-violet-100 text-violet-800',
    cardBorder: 'border-violet-300',
    chip: 'bg-white text-violet-700 border-violet-300 hover:bg-violet-50',
    chipActive: 'bg-violet-600 text-white border-violet-600 shadow-chunkySm',
  },
  reading: {
    emoji: '📖',
    label: 'Reading now!',
    cardBadge: 'bg-amber-100 text-amber-900',
    cardBorder: 'border-amber-300',
    chip: 'bg-white text-amber-700 border-amber-300 hover:bg-amber-50',
    chipActive: 'bg-amber-500 text-white border-amber-500 shadow-chunkySm',
  },
  read: {
    emoji: '⭐',
    label: 'Finished!',
    cardBadge: 'bg-emerald-100 text-emerald-900',
    cardBorder: 'border-emerald-300',
    chip: 'bg-white text-emerald-700 border-emerald-300 hover:bg-emerald-50',
    chipActive:
      'bg-emerald-500 text-white border-emerald-500 shadow-chunkySm',
  },
}

export const STATUS_ORDER: ReadingStatus[] = [
  'want-to-read',
  'reading',
  'read',
]
