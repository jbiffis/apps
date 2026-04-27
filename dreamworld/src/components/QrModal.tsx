import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import Icon from './Icon'

interface QrModalProps {
  url: string
  onClose: () => void
}

/**
 * Modal showing the audience-vote QR code, framed like a road-case
 * stencil plate. PNG download keeps the same dimensions as the rendered
 * QR (320 px) for a crisp scannable file.
 */
export default function QrModal({ url, onClose }: QrModalProps) {
  const [pngUrl, setPngUrl] = useState<string>('')

  useEffect(() => {
    let cancelled = false
    QRCode.toDataURL(url, {
      width: 640,
      margin: 1,
      color: {
        dark: '#0a0a0a',
        light: '#F4E5C8',
      },
      errorCorrectionLevel: 'M',
    })
      .then((dataUrl) => {
        if (!cancelled) setPngUrl(dataUrl)
      })
      .catch(() => {
        /* ignore */
      })
    return () => {
      cancelled = true
    }
  }, [url])

  function download() {
    if (!pngUrl) return
    const a = document.createElement('a')
    a.href = pngUrl
    a.download = 'dreamworld-vote-qr.png'
    document.body.appendChild(a)
    a.click()
    a.remove()
  }

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.78)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        zIndex: 90,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="panel pop-in"
        style={{
          padding: 22,
          width: '100%',
          maxWidth: 360,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
          <span className="dial-label">Audience portal</span>
          <button
            type="button"
            onClick={onClose}
            className="btn btn-ghost"
            style={{ padding: '6px 10px', fontSize: 11 }}
            aria-label="Close"
          >
            <Icon name="close" size={14} /> Close
          </button>
        </div>
        <h2 className="serif" style={{ fontSize: 22, color: 'var(--cream)', textAlign: 'center' }}>
          Scan to vote
        </h2>

        <div
          style={{
            background: 'var(--cream)',
            border: '4px solid var(--ink)',
            borderRadius: 8,
            padding: 14,
            boxShadow:
              'inset 0 0 0 2px var(--paper), 0 6px 0 rgba(0,0,0,0.6)',
          }}
        >
          {pngUrl ? (
            <img
              src={pngUrl}
              alt="QR code linking to the vote page"
              style={{ display: 'block', width: 240, height: 240 }}
            />
          ) : (
            <div
              style={{
                width: 240,
                height: 240,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--ink)',
                fontWeight: 700,
              }}
            >
              Generating…
            </div>
          )}
        </div>

        <div
          className="mono"
          style={{
            fontSize: 11,
            wordBreak: 'break-all',
            background: '#0a0807',
            color: 'var(--orange-bright)',
            padding: '8px 12px',
            borderRadius: 6,
            border: '1.5px solid var(--panel-edge)',
            width: '100%',
            textAlign: 'center',
          }}
        >
          {url}
        </div>

        <button
          type="button"
          onClick={download}
          className="btn btn-primary"
          disabled={!pngUrl}
          style={{ width: '100%', justifyContent: 'center' }}
        >
          <Icon name="download" size={14} /> Download QR
        </button>
      </div>
    </div>
  )
}
