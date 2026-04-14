import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Hole } from '../api'
import styles from './AdminPage.module.css'

interface Course { id: number; name: string; holes: Hole[] }

const DEFAULT_PAR = [4, 5, 4, 4, 3, 4, 4, 3, 5]

export function AdminCoursesPage() {
  const [courses, setCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [msg, setMsg] = useState('')

  // Create course form
  const [showForm, setShowForm] = useState(false)
  const [courseName, setCourseName] = useState('')
  const [coursePars, setCoursePars] = useState<string[]>(DEFAULT_PAR.map(String))
  const [courseYardages, setCourseYardages] = useState<string[]>(Array(9).fill(''))

  async function loadCourses() {
    const c = await api.get<Course[]>('/courses')
    setCourses(c)
    return c
  }

  useEffect(() => {
    loadCourses().finally(() => setLoading(false))
  }, [])

  async function createCourse() {
    if (!courseName.trim()) return
    setMsg('')
    try {
      const holes = coursePars.map((par, i) => ({
        hole_number: i + 1,
        par: parseInt(par) || 4,
        yardage: parseInt(courseYardages[i]) || 0,
      }))
      await api.post('/courses', { name: courseName.trim(), holes })
      await loadCourses()
      setShowForm(false)
      setCourseName('')
      setCoursePars(DEFAULT_PAR.map(String))
      setCourseYardages(Array(9).fill(''))
      setMsg(`Course "${courseName}" created.`)
    } catch { setMsg('Error creating course.') }
  }

  return (
    <div className={styles.page}>
      <div className={styles.headerRow}>
        <h1 className={styles.title}>
          <Link to="/admin" className={styles.breadcrumbLink}>Admin</Link> / Courses
        </h1>
        <div className={styles.controls}>
          <button className={styles.btnCreate} onClick={() => setShowForm(v => !v)}>
            + Course
          </button>
        </div>
      </div>

      {msg && <p className={styles.successMsg}>{msg}</p>}

      {showForm && (
        <div className={styles.formCard}>
          <h2 className={styles.formTitle}>Add Course</h2>
          <label className={styles.label} style={{ marginBottom: '0.75rem' }}>
            Course Name
            <input
              type="text"
              className={styles.input}
              value={courseName}
              onChange={e => setCourseName(e.target.value)}
              placeholder="e.g. Pebble Beach"
            />
          </label>
          <p className={styles.hint}>Enter par and yardage for each hole:</p>
          <div className={styles.holeGrid}>
            <div className={styles.holeHeader}>
              <span>Hole</span>
              {Array.from({ length: 9 }, (_, i) => <span key={i}>{i + 1}</span>)}
            </div>
            <div className={styles.holeRow}>
              <span className={styles.holeRowLabel}>Par</span>
              {coursePars.map((par, i) => (
                <input
                  key={i}
                  type="number"
                  min={3}
                  max={5}
                  className={styles.holeInput}
                  value={par}
                  onChange={e => {
                    const next = [...coursePars]
                    next[i] = e.target.value
                    setCoursePars(next)
                  }}
                />
              ))}
            </div>
            <div className={styles.holeRow}>
              <span className={styles.holeRowLabel}>Yds</span>
              {courseYardages.map((yds, i) => (
                <input
                  key={i}
                  type="number"
                  min={50}
                  max={700}
                  className={styles.holeInput}
                  value={yds}
                  placeholder="—"
                  onChange={e => {
                    const next = [...courseYardages]
                    next[i] = e.target.value
                    setCourseYardages(next)
                  }}
                />
              ))}
            </div>
          </div>
          <div className={styles.formActions}>
            <button className={styles.btnPrimary} onClick={createCourse} disabled={!courseName.trim()}>
              Add Course
            </button>
            <button className={styles.btnSecondary} onClick={() => setShowForm(false)}>Cancel</button>
          </div>
        </div>
      )}

      {loading && <p className={styles.loading}>Loading…</p>}

      {!loading && courses.length === 0 && (
        <p className={styles.empty}>No courses yet. Click <strong>+ Course</strong> to add one.</p>
      )}

      {courses.map(c => (
        <CourseCard key={c.id} course={c} onSave={loadCourses} setMsg={setMsg} />
      ))}
    </div>
  )
}

function CourseCard({ course, onSave, setMsg }: {
  course: Course
  onSave: () => Promise<unknown>
  setMsg: (m: string) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(course.name)
  const [pars, setPars] = useState(course.holes.map(h => String(h.par)))
  const [yardages, setYardages] = useState(course.holes.map(h => String(h.yardage || '')))
  const [saving, setSaving] = useState(false)

  function reset() {
    setName(course.name)
    setPars(course.holes.map(h => String(h.par)))
    setYardages(course.holes.map(h => String(h.yardage || '')))
    setEditing(false)
  }

  async function save() {
    setSaving(true)
    try {
      await api.put(`/courses/${course.id}`, {
        name,
        holes: course.holes.map((h, i) => ({
          hole_number: h.hole_number,
          par: parseInt(pars[i]) || h.par,
          yardage: parseInt(yardages[i]) || 0,
        })),
      })
      await onSave()
      setEditing(false)
      setMsg(`Course "${name}" updated.`)
    } catch {
      setMsg('Error updating course.')
    } finally {
      setSaving(false)
    }
  }

  const totalPar = pars.reduce((s, p) => s + (parseInt(p) || 0), 0)

  return (
    <div className={styles.courseCard}>
      <div className={styles.courseHeader}>
        {editing ? (
          <input className={styles.input} value={name} onChange={e => setName(e.target.value)} style={{ maxWidth: '240px' }} />
        ) : (
          <span className={styles.courseName}>{course.name}</span>
        )}
        <span className={styles.coursePar}>Par {totalPar}</span>
        {!editing && (
          <button className={styles.btnOutline} onClick={() => setEditing(true)} style={{ marginLeft: 'auto', padding: '0.3rem 0.75rem', fontSize: '0.8rem' }}>
            Edit
          </button>
        )}
      </div>
      <div className={styles.holeGrid}>
        <div className={styles.holeHeader}>
          <span>Hole</span>
          {course.holes.map((_, i) => <span key={i}>{i + 1}</span>)}
        </div>
        <div className={styles.holeRow}>
          <span className={styles.holeRowLabel}>Par</span>
          {pars.map((par, i) => (
            <input
              key={i}
              type="number"
              min={3}
              max={5}
              className={styles.holeInput}
              value={par}
              disabled={!editing}
              onChange={e => { const next = [...pars]; next[i] = e.target.value; setPars(next) }}
            />
          ))}
        </div>
        <div className={styles.holeRow}>
          <span className={styles.holeRowLabel}>Yds</span>
          {yardages.map((yds, i) => (
            <input
              key={i}
              type="number"
              min={50}
              max={700}
              className={styles.holeInput}
              value={yds}
              disabled={!editing}
              placeholder="—"
              onChange={e => { const next = [...yardages]; next[i] = e.target.value; setYardages(next) }}
            />
          ))}
        </div>
      </div>
      {editing && (
        <div className={styles.formActions}>
          <button className={styles.btnPrimary} onClick={save} disabled={saving || !name.trim()}>
            {saving ? 'Saving...' : 'Save'}
          </button>
          <button className={styles.btnSecondary} onClick={reset}>Cancel</button>
        </div>
      )}
    </div>
  )
}
