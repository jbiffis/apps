import { useNavigate, useParams } from 'react-router-dom'
import { useApi } from '../hooks/useApi.js'
import { Back, DynamicIcon } from '../icons/index.jsx'

// Epic 7 placeholder. The real entry form (field renderer, time chips, save +
// undo) lands in Epic 8 at this same route.
export default function Entry() {
  const { slug } = useParams()
  const navigate = useNavigate()
  const { data } = useApi(`/event-types/${slug}`)

  return (
    <div className="mx-auto flex min-h-full max-w-[480px] flex-col bg-bg">
      <header className="flex items-center gap-3 px-[18px] pb-2.5 pt-3">
        <button onClick={() => navigate(-1)} aria-label="Back"
          className="grid h-9 w-9 place-items-center rounded-full border border-line bg-surface text-ink-2">
          <Back size={18} />
        </button>
        <h1 className="font-display text-[22px] font-extrabold text-ink">{data?.name || 'Log'}</h1>
      </header>
      <main className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
        <span className="grid h-16 w-16 place-items-center rounded-2xl bg-accent-2 text-accent-ink">
          <DynamicIcon name={data?.icon} size={30} />
        </span>
        <p className="font-body text-[13px] text-ink-3">The entry form for this tracker is coming in Epic 8.</p>
      </main>
    </div>
  )
}
