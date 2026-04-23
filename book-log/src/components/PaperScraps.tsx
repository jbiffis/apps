export default function PaperScraps() {
  return (
    <>
      <div
        style={{
          position: 'absolute',
          top: 30,
          right: 30,
          width: 12,
          height: 12,
          background: 'var(--sticker-pink)',
          transform: 'rotate(25deg)',
          border: '1.5px solid var(--line)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: 90,
          left: 20,
          width: 10,
          height: 10,
          background: 'var(--sticker-blue)',
          transform: 'rotate(-15deg)',
          borderRadius: '50%',
          border: '1.5px solid var(--line)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: 110,
          right: 40,
          width: 16,
          height: 6,
          background: 'var(--sticker-green)',
          transform: 'rotate(35deg)',
          border: '1.5px solid var(--line)',
          borderRadius: 3,
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: 180,
          left: 40,
          width: 11,
          height: 11,
          background: 'var(--sticker-yellow)',
          transform: 'rotate(10deg)',
          border: '1.5px solid var(--line)',
        }}
      />
    </>
  )
}
