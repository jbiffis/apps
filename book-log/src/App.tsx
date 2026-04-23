import { Route, Routes } from 'react-router-dom'
import Add from './routes/Add'
import BookDetail from './routes/BookDetail'
import Home from './routes/Home'
import Profile from './routes/Profile'
import Quest from './routes/Quest'
import Shelves from './routes/Shelves'
import BottomNav from './components/BottomNav'
import OfflineBanner from './components/OfflineBanner'
import UpdatePrompt from './components/UpdatePrompt'

export default function App() {
  return (
    <div
      style={{
        minHeight: '100svh',
        maxWidth: 560,
        margin: '0 auto',
        position: 'relative',
        background: 'var(--bg)',
        boxShadow: '0 0 40px rgba(0,0,0,0.06)',
      }}
    >
      <OfflineBanner />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/shelves" element={<Shelves />} />
        <Route path="/add" element={<Add />} />
        <Route path="/quest" element={<Quest />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/book/:id" element={<BookDetail />} />
      </Routes>
      <BottomNav />
      <UpdatePrompt />
    </div>
  )
}
