export interface Song {
  id: string
  title: string
  artist: string
  addedAt: number
  votes: number
  /** Only present in audience-shaped responses. */
  voted?: boolean
}
