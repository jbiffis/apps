// Derive a consistent accent color + pattern from a book's ISBN so
// the look stays stable across renders even when we have no real cover.

export type BookPattern = 'stripes' | 'dots' | 'scales' | 'grid' | 'stars'

const PALETTE = [
  '#D9613A', // terracotta (accent-1)
  '#E8A93C', // mustard (accent-2)
  '#3E8C84', // teal (accent-3)
  '#C0496C', // berry (accent-4)
  '#6B8E4E', // moss (accent-5)
] as const

const PATTERNS: BookPattern[] = ['stripes', 'dots', 'scales', 'grid', 'stars']

function hashString(input: string): number {
  let hash = 0
  for (let i = 0; i < input.length; i++) {
    hash = (hash * 31 + input.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

export function bookColor(id: string): string {
  return PALETTE[hashString(id) % PALETTE.length]
}

export function bookPattern(id: string): BookPattern {
  return PATTERNS[hashString(`${id}-pattern`) % PATTERNS.length]
}
