import { useEffect, useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import Add from './routes/Add'
import BookDetail from './routes/BookDetail'
import Home from './routes/Home'
import Profile from './routes/Profile'
import Quest from './routes/Quest'
import Shelves from './routes/Shelves'
import BottomNav from './components/BottomNav'
import OfflineBanner from './components/OfflineBanner'
import Onboarding from './components/Onboarding'
import SplashScreen from './components/SplashScreen'
import UpdatePrompt from './components/UpdatePrompt'
import { useProfile } from './lib/profile'

export default function App() {
  const { profile } = useProfile()
  const [splashDone, setSplashDone] = useState(false)
  const [hideOnboarding, setHideOnboarding] = useState(false)

  // If the user already went through onboarding, never show it again.
  useEffect(() => {
    if (profile?.onboarded) setHideOnboarding(true)
  }, [profile?.onboarded])

  const needsOnboarding = splashDone && !hideOnboarding && !profile?.onboarded

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
      {splashDone && !needsOnboarding && <BottomNav />}
      <UpdatePrompt />
      {!splashDone && <SplashScreen onDone={() => setSplashDone(true)} />}
      {needsOnboarding && (
        <Onboarding onDone={() => setHideOnboarding(true)} />
      )}
    </div>
  )
}
