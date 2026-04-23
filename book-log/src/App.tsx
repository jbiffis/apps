import { Route, Routes } from 'react-router-dom'
import Library from './routes/Library'
import Scanner from './routes/Scanner'
import BookDetail from './routes/BookDetail'
import ManualAdd from './routes/ManualAdd'

export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <Routes>
        <Route path="/" element={<Library />} />
        <Route path="/scan" element={<Scanner />} />
        <Route path="/add" element={<ManualAdd />} />
        <Route path="/book/:id" element={<BookDetail />} />
      </Routes>
    </div>
  )
}
