import { db } from './db'
import type { Book } from './types'

const EXPORT_VERSION = 1

interface ExportPayload {
  version: number
  exportedAt: number
  books: Book[]
}

export async function exportLibrary(): Promise<void> {
  const books = await db.books.orderBy('dateAdded').toArray()
  const payload: ExportPayload = {
    version: EXPORT_VERSION,
    exportedAt: Date.now(),
    books,
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], {
    type: 'application/json',
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `booklog-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export async function importLibrary(file: File): Promise<{
  imported: number
  skipped: number
}> {
  const text = await file.text()
  const parsed = JSON.parse(text) as ExportPayload
  if (!parsed || !Array.isArray(parsed.books)) {
    throw new Error('Invalid export file.')
  }

  let imported = 0
  let skipped = 0
  for (const book of parsed.books) {
    if (!book.id || !book.isbn13 || !book.title) {
      skipped++
      continue
    }
    const existing = await db.books.get(book.id)
    if (existing) {
      skipped++
      continue
    }
    await db.books.put({
      ...book,
      tags: Array.isArray(book.tags) ? book.tags : [],
    })
    imported++
  }
  return { imported, skipped }
}
