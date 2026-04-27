import { Route, Routes } from 'react-router-dom'
import Audience from './routes/Audience'
import Musician from './routes/Musician'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Musician />} />
      <Route path="/vote" element={<Audience />} />
    </Routes>
  )
}
