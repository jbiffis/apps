export type ReadingStatus = 'read' | 'reading' | 'want-to-read'

export interface Book {
  id: string
  isbn10?: string
  isbn13: string
  title: string
  authors: string[]
  coverUrl?: string
  publisher?: string
  publishDate?: string
  pageCount?: number
  status: ReadingStatus
  rating?: 1 | 2 | 3 | 4 | 5
  notes?: string
  dateAdded: number
  dateFinished?: number
  tags: string[]
  /** Current page (only meaningful when status === 'reading'). */
  progress?: number
}
