import { readdir, readFile } from 'node:fs/promises'
import { dirname, extname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const modulePath = import.meta.url.startsWith('file:') ? fileURLToPath(import.meta.url) : undefined
const rootDir = modulePath ? resolve(dirname(modulePath), '..') : resolve(process.cwd())
const defaultSourceDir = rootDir
const defaultFoundationPath = join(rootDir, 'src', 'styles', 'foundation.css')
const sourceExtensions = new Set(['.css', '.html', '.js', '.jsx', '.mjs', '.ts', '.tsx', '.vue'])
const ignoredDirectoryNames = new Set([
  '.git',
  '__tests__',
  'coverage',
  'dist',
  'docs',
  'node_modules',
  'playwright-report',
  'test-results',
  'e2e',
])
const allowMarker = 'typography-guard-allow:'

async function collectSourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      return ignoredDirectoryNames.has(entry.name) ? [] : collectSourceFiles(path)
    }
    return sourceExtensions.has(extname(entry.name)) ? [path] : []
  }))

  return nested.flat()
}

function lineNumber(source, index) {
  return source.slice(0, index).split(/\r?\n/).length
}

function hasDocumentedException(source, index) {
  const linesBeforeMatch = source.slice(0, index).split(/\r?\n/)
  return linesBeforeMatch.slice(-2).some((line) => line.includes(allowMarker) && line.trim().length > allowMarker.length)
}

function recordViolation(violations, sourceDir, file, source, match, rule, message) {
  violations.push({
    file: relative(sourceDir, file).replaceAll('\\', '/'),
    line: lineNumber(source, match.index),
    rule,
    match: match[0],
    message,
  })
}

export async function checkTypographyTokenContracts(
  sourceDir = defaultSourceDir,
  foundationPath = defaultFoundationPath,
) {
  const foundationSource = await readFile(foundationPath, 'utf8')
  const definedTokens = new Set(
    [...foundationSource.matchAll(/(--(?:text|nv-type)-[\w-]+)\s*:/g)].map((match) => match[1]),
  )
  const files = await collectSourceFiles(sourceDir)
  const violations = []

  for (const file of files) {
    const source = file === foundationPath ? foundationSource : await readFile(file, 'utf8')

    for (const match of source.matchAll(/font-size\s*:\s*(?:0(?![\d.(])|(?:\d*\.)?\d+(?:px|rem|em)\b)/g)) {
      if (hasDocumentedException(source, match.index)) continue
      recordViolation(
        violations,
        sourceDir,
        file,
        source,
        match,
        'literal font size',
        `Use a typography token, or add a nearby "${allowMarker} reason" comment for an intentional exception.`,
      )
    }

    for (const match of source.matchAll(/fontSize\s*:\s*(?:\d*\.)?\d+\b/g)) {
      recordViolation(
        violations,
        sourceDir,
        file,
        source,
        match,
        'literal generated-media font size',
        'Use a named value from scripts/static-typography-tokens.mjs.',
      )
    }

    for (const match of source.matchAll(/(?:[\w-]+:)*text-\[(?:\d*\.)?\d+(?:px|rem|em)\]/g)) {
      recordViolation(
        violations,
        sourceDir,
        file,
        source,
        match,
        'arbitrary text size',
        'Use the shared text-micro, text-compact, text-body-compact, text-display, or standard text-* scale.',
      )
    }

    for (const match of source.matchAll(/font-size\s*:\s*var\(\s*(--[\w-]+)/g)) {
      const token = match[1]
      if (definedTokens.has(token)) continue
      recordViolation(
        violations,
        sourceDir,
        file,
        source,
        match,
        'undefined typography token',
        `Define ${token} in styles/foundation.css before using it as a font size.`,
      )
    }
  }

  return violations
}

async function run() {
  const violations = await checkTypographyTokenContracts()
  if (violations.length === 0) {
    console.log('Typography token guard passed.')
    return
  }

  console.error('Typography token guard failed.')
  for (const violation of violations) {
    console.error(`- ${violation.file}:${violation.line} [${violation.rule}] ${violation.match}`)
    console.error(`  ${violation.message}`)
  }
  process.exitCode = 1
}

const isDirectRun = modulePath && process.argv[1] && resolve(process.argv[1]) === modulePath

if (isDirectRun) {
  await run()
}
