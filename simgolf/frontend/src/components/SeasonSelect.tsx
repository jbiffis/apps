import { useEffect, useState } from 'react'
import { api, type Season } from '../api'
import styles from './SeasonSelect.module.css'

interface SeasonSelectProps {
  value: number | null
  onChange: (id: number) => void
}

export function SeasonSelect({ value, onChange }: SeasonSelectProps) {
  const [seasons, setSeasons] = useState<Season[]>([])

  useEffect(() => {
    api.get<Season[]>('/seasons').then(data => {
      setSeasons(data)
      if (!value && data.length > 0) onChange(data[0].id)
    })
  }, [])

  return (
    <div className={styles.wrap}>
      <label className={styles.label} htmlFor="season-select">Season</label>
      <select
        id="season-select"
        className={styles.select}
        value={value ?? ''}
        onChange={e => onChange(Number(e.target.value))}
      >
        {seasons.map(s => (
          <option key={s.id} value={s.id}>{s.name}</option>
        ))}
      </select>
    </div>
  )
}
