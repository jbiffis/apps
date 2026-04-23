export default function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-slate-500">
      <p>{message}</p>
    </div>
  )
}
