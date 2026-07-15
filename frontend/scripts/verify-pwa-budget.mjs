import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const DEFAULT_MAX_BYTES = 2_500_000
const DEFAULT_MAX_ENTRIES = 60
const DEFAULT_MAX_JS_CHUNK_BYTES = 450_000

export function extractPrecacheUrls(serviceWorkerSource) {
  return [...serviceWorkerSource.matchAll(/"url":"([^"]+)"/g)]
    .map((match) => match[1])
    .filter((url) => !/^https?:\/\//i.test(url))
}

export function measurePrecache({ distDirectory, serviceWorkerSource }) {
  const urls = [...new Set(extractPrecacheUrls(serviceWorkerSource))]
  if (urls.length === 0 || !urls.includes('index.html')) {
    throw new Error('Injected Workbox precache manifest is missing the app shell.')
  }
  let totalBytes = 0

  for (const url of urls) {
    const relativePath = decodeURIComponent(url.split(/[?#]/, 1)[0]).replace(/^\/+/, '')
    const filePath = path.resolve(distDirectory, relativePath)
    const relativeToDist = path.relative(distDirectory, filePath)
    if (relativeToDist.startsWith('..') || path.isAbsolute(relativeToDist)) {
      throw new Error(`Precache URL escapes dist: ${url}`)
    }
    if (!existsSync(filePath)) {
      throw new Error(`Precache asset is missing from dist: ${url}`)
    }
    totalBytes += statSync(filePath).size
  }

  return { urls, totalBytes }
}

export function extractStaticImportSpecifiers(source) {
  return [...source.matchAll(/(?:\bfrom\s*|\bimport\s*)["'](\.{1,2}\/[^"']+)["']/g)]
    .map((match) => match[1])
}

export function findMissingPrecacheStaticImports(precacheUrls, readSourceForUrl) {
  const normalizedUrls = new Set(
    precacheUrls.map((url) => decodeURIComponent(url.split(/[?#]/, 1)[0]).replace(/^\/+/, '')),
  )
  const missing = []

  for (const importer of normalizedUrls) {
    if (!importer.endsWith('.js')) continue

    for (const specifier of extractStaticImportSpecifiers(readSourceForUrl(importer))) {
      const imported = path.posix
        .normalize(path.posix.join(path.posix.dirname(importer), specifier.split(/[?#]/, 1)[0]))
        .replace(/^\/+/, '')
      if (!normalizedUrls.has(imported)) {
        missing.push({ importer, imported })
      }
    }
  }

  return missing
}

export function verifyPrecacheStaticImportClosure({ distDirectory, precacheUrls }) {
  const missing = findMissingPrecacheStaticImports(precacheUrls, (url) => {
    const filePath = path.resolve(distDirectory, url)
    const relativeToDist = path.relative(distDirectory, filePath)
    if (relativeToDist.startsWith('..') || path.isAbsolute(relativeToDist)) {
      throw new Error(`Precache URL escapes dist: ${url}`)
    }
    return readFileSync(filePath, 'utf8')
  })

  if (missing.length > 0) {
    throw new Error(
      `PWA precache static import closure is incomplete: ${missing
        .map(({ importer, imported }) => `${importer} -> ${imported}`)
        .join(', ')}`,
    )
  }
}

export function verifyPrecacheBudget({ totalBytes, entryCount, maxBytes, maxEntries }) {
  if (totalBytes > maxBytes) {
    throw new Error(`PWA precache is ${totalBytes} bytes; budget is ${maxBytes} bytes.`)
  }
  if (entryCount > maxEntries) {
    throw new Error(`PWA precache has ${entryCount} entries; budget is ${maxEntries} entries.`)
  }
}

export function measureJavaScriptChunks(distDirectory) {
  const jsDirectory = path.join(distDirectory, 'js')
  if (!existsSync(jsDirectory)) return []
  return readdirSync(jsDirectory, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.js'))
    .map((entry) => {
      const filePath = path.join(entry.parentPath, entry.name)
      return { file: path.relative(distDirectory, filePath).replaceAll('\\', '/'), bytes: statSync(filePath).size }
    })
}

export function verifyJavaScriptChunkBudget(chunks, maxBytes) {
  const oversized = chunks.filter((chunk) => chunk.bytes > maxBytes)
  if (oversized.length > 0) {
    throw new Error(
      `JavaScript chunk budget exceeded (${maxBytes} bytes): ${oversized.map((chunk) => `${chunk.file}=${chunk.bytes}`).join(', ')}`,
    )
  }
}

function positiveInteger(value, fallback, name) {
  if (value == null || value === '') return fallback
  const parsed = Number(value)
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer.`)
  }
  return parsed
}

export function runPrecacheBudgetCheck({
  cwd = process.cwd(),
  maxBytes = positiveInteger(process.env.PWA_PRECACHE_BUDGET_BYTES, DEFAULT_MAX_BYTES, 'PWA_PRECACHE_BUDGET_BYTES'),
  maxEntries = positiveInteger(process.env.PWA_PRECACHE_MAX_ENTRIES, DEFAULT_MAX_ENTRIES, 'PWA_PRECACHE_MAX_ENTRIES'),
  maxJsChunkBytes = positiveInteger(process.env.PWA_MAX_JS_CHUNK_BYTES, DEFAULT_MAX_JS_CHUNK_BYTES, 'PWA_MAX_JS_CHUNK_BYTES'),
} = {}) {
  const distDirectory = path.resolve(cwd, 'dist')
  const serviceWorkerPath = path.join(distDirectory, 'service-worker.js')
  if (!existsSync(serviceWorkerPath)) {
    throw new Error('dist/service-worker.js is missing. Run the production build first.')
  }

  const serviceWorkerSource = readFileSync(serviceWorkerPath, 'utf8')
  const measurement = measurePrecache({ distDirectory, serviceWorkerSource })
  verifyPrecacheStaticImportClosure({ distDirectory, precacheUrls: measurement.urls })
  verifyPrecacheBudget({
    totalBytes: measurement.totalBytes,
    entryCount: measurement.urls.length,
    maxBytes,
    maxEntries,
  })
  const chunks = measureJavaScriptChunks(distDirectory)
  verifyJavaScriptChunkBudget(chunks, maxJsChunkBytes)

  const mib = (measurement.totalBytes / 1024 / 1024).toFixed(2)
  const budgetMib = (maxBytes / 1024 / 1024).toFixed(2)
  process.stdout.write(
    `PWA precache budget OK: ${measurement.urls.length}/${maxEntries} entries, ${mib}/${budgetMib} MiB, JS chunks <= ${maxJsChunkBytes} bytes.\n`,
  )
  return measurement
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : null
if (invokedPath === import.meta.url) {
  try {
    runPrecacheBudgetCheck()
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`)
    process.exitCode = 1
  }
}
