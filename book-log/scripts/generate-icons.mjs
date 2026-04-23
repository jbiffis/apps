import sharp from 'sharp'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..')
const SVG_PATH = resolve(ROOT, 'public/favicon.svg')
const OUT_DIR = resolve(ROOT, 'public/icons')

async function render(sizedSvg, size, outPath) {
  await sharp(Buffer.from(sizedSvg))
    .resize(size, size)
    .png()
    .toFile(outPath)
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true })
  const svg = await readFile(SVG_PATH, 'utf8')

  // Standard icons (transparent corners preserved).
  await render(svg, 192, resolve(OUT_DIR, 'icon-192.png'))
  await render(svg, 512, resolve(OUT_DIR, 'icon-512.png'))

  // Maskable: add safe-area padding so Android's mask doesn't clip the book.
  const maskable = svg.replace(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
    [
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
      '<rect width="64" height="64" fill="#0f172a"/>',
      '<g transform="translate(10 10) scale(0.6875)">',
    ].join(''),
  ).replace('</svg>', '</g></svg>')
  await render(maskable, 512, resolve(OUT_DIR, 'maskable-512.png'))

  // Apple touch icon: needs solid background, no transparency.
  const appleSvg = svg.replace(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
    [
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
      '<rect width="64" height="64" fill="#0f172a"/>',
    ].join(''),
  )
  await render(appleSvg, 180, resolve(OUT_DIR, 'apple-touch-icon.png'))

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
