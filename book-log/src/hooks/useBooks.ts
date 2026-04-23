import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '../lib/db'
import type { Book } from '../lib/types'

export function useBooks(): Book[] | undefined {
  return useLiveQuery(() =>
    db.books.orderBy('dateAdded').reverse().toArray(),
  )
}

export function useBook(id: string | undefined): Book | undefined | null {
  return useLiveQuery(async () => {
    if (!id) return null
    return (await db.books.get(id)) ?? null
  }, [id])
}
