import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import Library from './routes/Library'
import ManualAdd from './routes/ManualAdd'
import BookDetail from './routes/BookDetail'
import Stats from './routes/Stats'
import OfflineBanner from './components/OfflineBanner'
import UpdatePrompt from './components/UpdatePrompt'

const Scanner = lazy(() => import('./routes/Scanner'))

export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <OfflineBanner />
      <Suspense
        fallback={
          <div className="mx-auto w-full max-w-6xl px-4 py-6 text-sm text-slate-500">
            Loading…
          </div>
        }
      >
        <Routes>
          <Route path="/" element={<Library />} />
          <Route path="/scan" element={<Scanner />} />
          <Route path="/add" element={<ManualAdd />} />
          <Route path="/book/:id" element={<BookDetail />} />
          <Route path="/stats" element={<Stats />} />
        </Routes>
      </Suspense>
      <UpdatePrompt />
    </div>
  )
}
