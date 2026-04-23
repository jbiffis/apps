import type { Book } from './types'

const OL_ENDPOINT = 'https://openlibrary.org/api/books'
const GB_ENDPOINT = 'https://www.googleapis.com/books/v1/volumes'

export type BookLookup = Omit<
  Book,
  'status' | 'dateAdded' | 'dateFinished' | 'tags' | 'rating' | 'notes'
>

interface OLAuthor {
  name?: string
}
interface OLPublisher {
  name?: string
}
interface OLCover {
  small?: string
  medium?: string
  large?: string
}
interface OLEntry {
  title?: string
  authors?: OLAuthor[]
  cover?: OLCover
  publishers?: OLPublisher[]
  publish_date?: string
  number_of_pages?: number
}

export async function lookupOpenLibrary(
  isbn13: string,
): Promise<BookLookup | null> {
  const url = `${OL_ENDPOINT}?bibkeys=ISBN:${isbn13}&format=json&jscmd=data`
  const res = await fetch(url)
  if (!res.ok) return null
  const data = (await res.json()) as Record<string, OLEntry>
  const entry = data[`ISBN:${isbn13}`]
  if (!entry || !entry.title) return null
  const coverUrl =
    entry.cover?.large ||
    entry.cover?.medium ||
    `https://covers.openlibrary.org/b/isbn/${isbn13}-L.jpg?default=false`
  return {
    id: isbn13,
    isbn13,
    title: entry.title,
    authors: entry.authors?.map((a) => a.name ?? '').filter(Boolean) ?? [],
    coverUrl,
    publisher: entry.publishers?.[0]?.name,
    publishDate: entry.publish_date,
    pageCount: entry.number_of_pages,
  }
}

interface GBVolumeInfo {
  title?: string
  authors?: string[]
  publisher?: string
  publishedDate?: string
  pageCount?: number
  imageLinks?: { thumbnail?: string; smallThumbnail?: string }
}
interface GBItem {
  volumeInfo?: GBVolumeInfo
}
interface GBResponse {
  items?: GBItem[]
}

export async function lookupGoogleBooks(
  isbn13: string,
): Promise<BookLookup | null> {
  const url = `${GB_ENDPOINT}?q=isbn:${isbn13}`
  const res = await fetch(url)
  if (!res.ok) return null
  const data = (await res.json()) as GBResponse
  const info = data.items?.[0]?.volumeInfo
  if (!info || !info.title) return null
  const cover = info.imageLinks?.thumbnail || info.imageLinks?.smallThumbnail
  return {
    id: isbn13,
    isbn13,
    title: info.title,
    authors: info.authors ?? [],
    coverUrl: cover?.replace(/^http:/, 'https:'),
    publisher: info.publisher,
    publishDate: info.publishedDate,
    pageCount: info.pageCount,
  }
}

export async function lookupBook(isbn13: string): Promise<BookLookup | null> {
  try {
    const ol = await lookupOpenLibrary(isbn13)
    if (ol) return ol
  } catch {
    // fall through to Google Books
  }
  try {
    return await lookupGoogleBooks(isbn13)
  } catch {
    return null
  }
}
