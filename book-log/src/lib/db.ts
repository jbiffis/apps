import Dexie, { type Table } from 'dexie'
import type { Book } from './types'

class BookLogDB extends Dexie {
  books!: Table<Book, string>

  constructor() {
    super('booklog')
    this.version(1).stores({
      books: 'id, status, dateAdded, *tags',
    })
    // v2: no index changes; progress field is not indexed.
    this.version(2).stores({
      books: 'id, status, dateAdded, *tags',
    })
  }
}

export const db = new BookLogDB()

export async function addBook(book: Book): Promise<void> {
  await db.books.put(book)
}

export async function getBook(id: string): Promise<Book | undefined> {
  return db.books.get(id)
}

export async function deleteBook(id: string): Promise<void> {
  await db.books.delete(id)
}

export async function updateBook(id: string, changes: Partial<Book>): Promise<void> {
  await db.books.update(id, changes)
}
