import { useEffect, useRef, useState } from 'react'
import {
  BrowserMultiFormatReader,
  type IScannerControls,
} from '@zxing/browser'
import { BarcodeFormat, DecodeHintType } from '@zxing/library'
import { isBookIsbn13 } from '../lib/isbn'

interface BarcodeScannerProps {
  onDetected: (isbn13: string) => void
  paused?: boolean
}

export default function BarcodeScanner({
  onDetected,
  paused = false,
}: BarcodeScannerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const controlsRef = useRef<IScannerControls | null>(null)
  const onDetectedRef = useRef(onDetected)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(true)

  useEffect(() => {
    onDetectedRef.current = onDetected
  }, [onDetected])

  useEffect(() => {
    if (paused) return

    const hints = new Map()
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.EAN_13])
    const reader = new BrowserMultiFormatReader(hints)

    let cancelled = false
    setStarting(true)
    setError(null)

    const video = videoRef.current
    if (!video) return

    reader
      .decodeFromVideoDevice(
        undefined,
        video,
        (result, _err, controls) => {
          if (cancelled) return
          if (!controlsRef.current) {
            controlsRef.current = controls
            setStarting(false)
          }
          if (result) {
            const text = result.getText()
            if (isBookIsbn13(text)) {
              onDetectedRef.current(text)
            }
          }
        },
      )
      .catch((e: unknown) => {
        if (cancelled) return
        const message =
          e instanceof Error ? e.message : 'Could not access camera.'
        setError(message)
        setStarting(false)
      })

    return () => {
      cancelled = true
      controlsRef.current?.stop()
      controlsRef.current = null
    }
  }, [paused])

  return (
    <div className="relative overflow-hidden rounded-lg bg-black">
      <video
        ref={videoRef}
        className="block aspect-[3/4] w-full object-cover"
        playsInline
        muted
      />
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <div className="h-1/3 w-5/6 rounded-md border-2 border-white/70 shadow-[0_0_0_9999px_rgba(0,0,0,0.35)]" />
      </div>
      {starting && !error && (
        <div className="absolute inset-0 flex items-center justify-center text-white/90">
          Starting camera…
        </div>
      )}
      {error && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/70 px-6 text-center text-sm text-white">
          <p>{error}</p>
          <p className="text-white/70">
            Make sure camera access is allowed and you’re on HTTPS.
          </p>
        </div>
      )}
    </div>
  )
}
