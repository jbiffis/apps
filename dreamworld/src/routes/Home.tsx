export default function Home() {
  return (
    <main
      className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 text-center"
      style={{
        background:
          'radial-gradient(ellipse at top, #312e81 0%, #1e1b4b 45%, #0f172a 100%)',
      }}
    >
      {/* Stars */}
      {Array.from({ length: 60 }).map((_, i) => {
        const top = Math.random() * 100
        const left = Math.random() * 100
        const size = 1 + Math.random() * 2
        const opacity = 0.3 + Math.random() * 0.7
        return (
          <span
            key={i}
            aria-hidden
            className="absolute rounded-full bg-white"
            style={{
              top: `${top}%`,
              left: `${left}%`,
              width: size,
              height: size,
              opacity,
            }}
          />
        )
      })}

      <div className="relative z-10 mx-auto max-w-md">
        <div className="mb-6 text-7xl">🌙</div>
        <h1 className="text-4xl font-bold tracking-tight text-white sm:text-5xl">
          Dreamworld
        </h1>
        <p className="mt-3 text-base text-indigo-200">
          Coming soon. ✨
        </p>
      </div>
    </main>
  )
}
