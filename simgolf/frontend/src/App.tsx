import { Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { SeasonSummaryPage } from './pages/SeasonSummary'
import { TournamentPage } from './pages/TournamentPage'
import { RoundPage } from './pages/RoundPage'
import { PlayerPage } from './pages/PlayerPage'
import { HandicapPage } from './pages/HandicapPage'
import { PrizesPage } from './pages/PrizesPage'
import { InRoundPage } from './pages/InRoundPage'
import { AdminPage } from './pages/AdminPage'
import { AdminScoreEntryPage } from './pages/AdminScoreEntryPage'

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<SeasonSummaryPage />} />
        <Route path="/tournament/:id" element={<TournamentPage />} />
        <Route path="/round/:id" element={<RoundPage />} />
        <Route path="/player/:id" element={<PlayerPage />} />
        <Route path="/handicap" element={<HandicapPage />} />
        <Route path="/prizes" element={<PrizesPage />} />
        <Route path="/in-round/:id" element={<InRoundPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/admin/rounds/:id/scores" element={<AdminScoreEntryPage />} />
      </Routes>
    </Layout>
  )
}

export default App
