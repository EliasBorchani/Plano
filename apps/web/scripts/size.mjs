// Size report for the KMP-to-JS slice: run after `npm run build` (and `npm run shared`).
//   node scripts/size.mjs
// 1. The production bundle (raw, gzip, brotli).
// 2. A baseline: the same React + React Router entry without the Kotlin layer.
// 3. The Kotlin library split by origin (stdlib, coroutines, Ktor…), from its source map.
import { readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { brotliCompressSync, gzipSync } from 'node:zlib'
import { build } from 'vite'

const kb = (n) => `${(n / 1024).toFixed(1)} KB`
const sizes = (buffer) => ({ raw: buffer.length, gzip: gzipSync(buffer).length, brotli: brotliCompressSync(buffer).length })
const line = (label, s) => console.log(`${label.padEnd(28)} ${kb(s.raw).padStart(10)} ${kb(s.gzip).padStart(10)} ${kb(s.brotli).padStart(10)}`)

function jsOf(dir) {
  const assets = join(dir, 'assets')
  return Buffer.concat(readdirSync(assets).filter((f) => f.endsWith('.js')).map((f) => readFileSync(join(assets, f))))
}

console.log(`${''.padEnd(28)} ${'raw'.padStart(10)} ${'gzip'.padStart(10)} ${'brotli'.padStart(10)}`)
const app = sizes(jsOf('dist'))
line('App bundle (all JS)', app)

// Baseline: React, React DOM and React Router, as the app uses them, and nothing else.
const tmp = 'scripts/.baseline'
rmSync(tmp, { recursive: true, force: true })
writeFileSync(
  'scripts/baseline-entry.tsx',
  `import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
const router = createBrowserRouter([{ path: '/', element: <p>baseline</p> }])
createRoot(document.getElementById('root')!).render(<RouterProvider router={router} />)
`,
)
await build({
  logLevel: 'silent',
  configFile: false,
  build: { outDir: tmp, emptyOutDir: true, lib: false, rolldownOptions: { input: 'scripts/baseline-entry.tsx' } },
})
const baseline = sizes(jsOf(tmp))
rmSync(tmp, { recursive: true, force: true })
rmSync('scripts/baseline-entry.tsx')
line('React-only baseline', baseline)
line('Cost of the Kotlin layer', {
  raw: app.raw - baseline.raw,
  gzip: app.gzip - baseline.gzip,
  brotli: app.brotli - baseline.brotli,
})

// Split of the (unminified) Kotlin output by source, from its source map.
const lib = '../../bridge-web/build/dist/js/productionLibrary'
const code = readFileSync(join(lib, 'plano-shared.mjs'), 'utf8')
const map = JSON.parse(readFileSync(join(lib, 'plano-shared.mjs.map'), 'utf8'))

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
function decodeVlq(segment) {
  const values = []
  let value = 0
  let shift = 0
  for (const char of segment) {
    const digit = B64.indexOf(char)
    value += (digit & 31) << shift
    if (digit & 32) {
      shift += 5
    } else {
      values.push(value & 1 ? -(value >>> 1) : value >>> 1)
      value = 0
      shift = 0
    }
  }
  return values
}

// Library sources keep the path of the CI machine that built them (`…/work/<hash>/…`).
// Sources sharing a build folder belong to one library, named here from a file it contains.
const SIGNATURES = [
  [/kotlinx-coroutines/, 'kotlinx-coroutines'],
  [/ktor-/, 'ktor-client'],
  [/koin/i, 'koin'],
  [/androidx-lifecycle/, 'androidx-lifecycle'],
  [/Stately|atomicfu/, 'atomicfu, stately'],
  [/LocalDate/, 'kotlinx-datetime'],
  [/JsonElement|SerialDescriptor/, 'kotlinx-serialization'],
  [/ByteString|Sink\.kt/, 'kotlinx-io'],
  [/org\.w3c/, 'kotlin-wrappers (DOM)'],
]
const buildRoot = (source) => source.replaceAll('\\', '/').match(/^(?:\.\.\/)*(.*?\/work\/[^/]+)\//)?.[1]
const rootLabels = new Map()
for (const source of map.sources) {
  const root = buildRoot(source)
  const label = SIGNATURES.find(([re]) => re.test(source))?.[1]
  if (root && label && !rootLabels.has(root)) rootLabels.set(root, label)
}

function group(source) {
  const s = source.replaceAll('\\', '/')
  if (/(^|\/)(features|domain|contract|bridge-web|utilities)\//.test(s) && !buildRoot(s)) return 'plano (our code)'
  const root = buildRoot(s)
  if (root) return rootLabels.get(root) ?? SIGNATURES.find(([re]) => re.test(s))?.[1] ?? 'other libraries'
  return 'kotlin-stdlib'
}

const lines = code.split('\n')
const bytes = new Map()
let sourceIndex = 0
map.mappings.split(';').forEach((mapped, lineNo) => {
  const text = lines[lineNo] ?? ''
  let column = 0
  const segments = mapped ? mapped.split(',').map(decodeVlq) : []
  segments.forEach((segment, i) => {
    column += segment[0]
    if (segment.length > 1) sourceIndex += segment[1]
    const nextColumn = i + 1 < segments.length ? column + segments[i + 1][0] : text.length
    if (segment.length > 1) {
      const name = group(map.sources[sourceIndex] ?? '')
      bytes.set(name, (bytes.get(name) ?? 0) + Math.max(0, nextColumn - column))
    }
  })
})
const mappedTotal = [...bytes.values()].reduce((a, b) => a + b, 0)
console.log(`\nKotlin library, unminified: ${kb(code.length)}; split of the ${kb(mappedTotal)} the source map attributes:`)
for (const [name, n] of [...bytes].sort((a, b) => b[1] - a[1])) {
  console.log(`  ${name.padEnd(32)} ${kb(n).padStart(10)}  ${((n / mappedTotal) * 100).toFixed(1).padStart(5)} %`)
}
