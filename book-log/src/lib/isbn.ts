export function normalizeIsbn(input: string): string {
  return input.replace(/[-\s]/g, '').toUpperCase()
}

export function isValidIsbn10(raw: string): boolean {
  const isbn = normalizeIsbn(raw)
  if (!/^\d{9}[\dX]$/.test(isbn)) return false
  let sum = 0
  for (let i = 0; i < 10; i++) {
    const ch = isbn[i]
    const digit = ch === 'X' ? 10 : Number(ch)
    sum += digit * (10 - i)
  }
  return sum % 11 === 0
}

export function isValidIsbn13(raw: string): boolean {
  const isbn = normalizeIsbn(raw)
  if (!/^\d{13}$/.test(isbn)) return false
  let sum = 0
  for (let i = 0; i < 13; i++) {
    sum += Number(isbn[i]) * (i % 2 === 0 ? 1 : 3)
  }
  return sum % 10 === 0
}

export function isValidIsbn(raw: string): boolean {
  const isbn = normalizeIsbn(raw)
  return isbn.length === 10 ? isValidIsbn10(isbn) : isValidIsbn13(isbn)
}

export function isBookIsbn13(raw: string): boolean {
  const isbn = normalizeIsbn(raw)
  return (
    isValidIsbn13(isbn) && (isbn.startsWith('978') || isbn.startsWith('979'))
  )
}

export function isbn10To13(raw: string): string | null {
  const isbn = normalizeIsbn(raw)
  if (!isValidIsbn10(isbn)) return null
  const core = '978' + isbn.slice(0, 9)
  let sum = 0
  for (let i = 0; i < 12; i++) {
    sum += Number(core[i]) * (i % 2 === 0 ? 1 : 3)
  }
  const check = (10 - (sum % 10)) % 10
  return core + check
}

export function toIsbn13(raw: string): string | null {
  const isbn = normalizeIsbn(raw)
  if (isValidIsbn13(isbn)) return isbn
  return isbn10To13(isbn)
}
