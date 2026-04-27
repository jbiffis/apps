import sharp from 'sharp'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..')
const SVG_PATH = resolve(ROOT, 'public/favicon.svg')
const OUT_DIR = resolve(ROOT, 'public/icons')

const BG = '#1e1b4b'

async function render(svgString, size, outPath) {
  await sharp(Buffer.from(svgString)).resize(size, size).png().toFile(outPath)
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true })
  const svg = await readFile(SVG_PATH, 'utf8')

  await render(svg, 192, resolve(OUT_DIR, 'icon-192.png'))
  await render(svg, 512, resolve(OUT_DIR, 'icon-512.png'))

  // Maskable: add safe-area padding so Android's mask doesn't clip the moon.
  const maskable = svg
    .replace(
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
      [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
        `<rect width="64" height="64" fill="${BG}"/>`,
        '<g transform="translate(10 10) scale(0.6875)">',
      ].join(''),
    )
    .replace('</svg>', '</g></svg>')
  await render(maskable, 512, resolve(OUT_DIR, 'maskable-512.png'))

  // Apple touch icon: opaque background.
  const appleSvg = svg.replace(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
    [
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
      `<rect width="64" height="64" fill="${BG}"/>`,
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
