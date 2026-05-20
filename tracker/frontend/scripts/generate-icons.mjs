import sharp from 'sharp'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..')
const SVG_PATH = resolve(ROOT, 'public/favicon.svg')
const OUT_DIR = resolve(ROOT, 'public/icons')

const OPEN = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">'

async function render(svg, size, outPath) {
  await sharp(Buffer.from(svg)).resize(size, size).png().toFile(outPath)
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true })
  const svg = await readFile(SVG_PATH, 'utf8')

  // Standard icons (the green rounded square already fills the canvas).
  await render(svg, 192, resolve(OUT_DIR, 'icon-192.png'))
  await render(svg, 512, resolve(OUT_DIR, 'icon-512.png'))

  // Maskable: pad the mark into the safe area so Android's mask doesn't clip
  // the rounded corners. Cream backdrop matches the app background.
  const maskable = svg
    .replace(OPEN, `${OPEN}<rect width="64" height="64" fill="#f6efe4"/><g transform="translate(8 8) scale(0.75)">`)
    .replace('</svg>', '</g></svg>')
  await render(maskable, 512, resolve(OUT_DIR, 'maskable-512.png'))

  // Apple touch icon: solid background, no transparency.
  const apple = svg.replace(OPEN, `${OPEN}<rect width="64" height="64" fill="#f6efe4"/>`)
  await render(apple, 180, resolve(OUT_DIR, 'apple-touch-icon.png'))

  await writeFile(
    resolve(OUT_DIR, 'README.txt'),
    'Generated from public/favicon.svg via scripts/generate-icons.mjs\n',
  )
  console.log('Icons generated in', OUT_DIR)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
