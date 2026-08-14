import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = fileURLToPath(new URL('..', import.meta.url))
const srcDir = join(rootDir, 'src')
const foundationPath = join(srcDir, 'styles', 'foundation.css')
const extensions = new Set(['.vue', '.ts', '.css'])
const maxThemeBaseColors = 28
const deprecatedNvTokens = new Map([
  ['--nv-text', '--nv-ink'],
  ['--nv-border', '--nv-line'],
  ['--nv-page', '--nv-bg'],
  ['--nv-text-subtle', '--nv-muted'],
  ['--nv-text-muted', '--nv-ink-soft'],
  ['--nv-surface-alt', '--nv-surface-2'],
  ['--nv-accent-soft', '--nv-accent-bg'],
])

const rules = [
  {
    name: 'raw status text color',
    pattern: /(?<![-\w])color\s*:\s*var\(\s*--nv-(?:danger|warning|success)\s*\)/g,
    message: 'Use the corresponding --nv-*-text token for text. Raw status tokens are reserved for backgrounds, borders, and icons.',
  },
  {
    name: 'raw status Tailwind text color',
    pattern: /(?:[\w-]+:)*text-\[var\(\s*--nv-(?:danger|warning|success)\s*\)\]/g,
    message: 'Use the corresponding --nv-*-text token for text. Raw status tokens are reserved for backgrounds, borders, and icons.',
  },
  {
    name: 'dark brand background',
    pattern: /\b[\w:-]*dark:[\w:-]*bg-(?:blue|indigo|sky|cyan)-\S*/g,
    message: 'Use nv-hover-surface, nv-active-surface, nv-status-info, or an nv token instead of dark blue/indigo/cyan backgrounds.',
  },
  {
    name: 'paired surface background',
    pattern: /\b[\w:-]*bg-(?:white|gray-50)\b[^\n"'`]*\b[\w:-]*dark:bg-gray-\S*/g,
    message: 'Use nv-surface or nv-surface-muted instead of paired light/dark gray surface backgrounds.',
  },
  {
    name: 'paired border',
    pattern: /\b[\w:-]*border-gray-200\b[^\n"'`]*\b[\w:-]*dark:border-gray-\S*/g,
    message: 'Use border nv-border or nv-border-strong instead of paired gray borders.',
  },
  {
    name: 'paired primary text',
    pattern: /\b[\w:-]*text-gray-900\b[^\n"'`]*\b[\w:-]*dark:text-\S*/g,
    message: 'Use nv-title or nv-text instead of paired gray primary text colors.',
  },
  {
    name: 'paired muted text',
    pattern: /\b[\w:-]*text-gray-500\b[^\n"'`]*\b[\w:-]*dark:text-gray-\S*/g,
    message: 'Use nv-text-muted or nv-text-subtle instead of paired muted gray text colors.',
  },
  {
    name: 'invalid nv variant',
    pattern: /\b(?:hover|focus|active|dark|sm|md|lg|xl):nv-[\w-]+/g,
    message: 'Custom nv classes are plain CSS classes. Add a dedicated nv utility class instead of using Tailwind variants.',
  },
]

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const fullPath = join(dir, entry)
    const stats = statSync(fullPath)

    if (stats.isDirectory()) {
      return walk(fullPath)
    }

    const extension = fullPath.slice(fullPath.lastIndexOf('.'))
    return extensions.has(extension) ? [fullPath] : []
  })
}

function extractCssBlock(source, selectorPattern) {
  const selectorMatch = selectorPattern.exec(source)
  if (selectorMatch == null) {
    throw new Error(`Unable to find theme selector: ${selectorPattern}`)
  }

  const openingBrace = source.indexOf('{', selectorMatch.index)
  let depth = 0

  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    if (source[index] !== '}') continue

    depth -= 1
    if (depth === 0) {
      return source.slice(openingBrace + 1, index)
    }
  }

  throw new Error(`Unable to read theme block: ${selectorPattern}`)
}

function countUniqueBaseColors(block) {
  const colorSource = block
    .split(/\r?\n/)
    .filter((line) => !line.includes('--nv-shadow-'))
    .join('\n')
  const colors = colorSource.match(/#[0-9a-f]{3,8}\b|rgba?\([^)]*\)|hsla?\([^)]*\)/gi) ?? []

  return new Set(colors.map((color) => color.toLowerCase())).size
}

const files = walk(srcDir)
const violations = []
const definedNvTokens = new Set()
const foundationSource = readFileSync(foundationPath, 'utf8')
const themeBaseColorCounts = {
  light: countUniqueBaseColors(extractCssBlock(foundationSource, /^\s*:root\s*\{/m)),
  dark: countUniqueBaseColors(extractCssBlock(foundationSource, /^\s*\.dark:root,/m)),
}

for (const file of files) {
  const source = readFileSync(file, 'utf8')
  for (const match of source.matchAll(/(--nv-[\w-]+)\s*:/g)) {
    definedNvTokens.add(match[1])
  }
}

for (const [theme, count] of Object.entries(themeBaseColorCounts)) {
  if (count <= maxThemeBaseColors) continue

  violations.push({
    file: relative(rootDir, foundationPath).replaceAll('\\', '/'),
    line: theme === 'light' ? 13 : 93,
    rule: 'theme base color budget',
    match: `${count} base colors`,
    message: `Keep each theme at or below ${maxThemeBaseColors} unique base colors by reusing semantic tokens or deriving variants with color-mix().`,
  })
}

for (const file of files) {
  const source = readFileSync(file, 'utf8')

  for (const rule of rules) {
    for (const match of source.matchAll(rule.pattern)) {
      const before = source.slice(0, match.index)
      const line = before.split(/\r?\n/).length
      violations.push({
        file: relative(rootDir, file).replaceAll('\\', '/'),
        line,
        rule: rule.name,
        match: match[0],
        message: rule.message,
      })
    }
  }

  for (const match of source.matchAll(/var\(\s*(--nv-(?:text-subtle|text-muted|surface-alt|accent-soft|text|border|page))\s*[,)]/g)) {
    const token = match[1]
    const replacement = deprecatedNvTokens.get(token)
    const before = source.slice(0, match.index)
    const line = before.split(/\r?\n/).length
    violations.push({
      file: relative(rootDir, file).replaceAll('\\', '/'),
      line,
      rule: 'deprecated nv token',
      match: token,
      message: `Use ${replacement} instead. Deprecated aliases are compatibility declarations only.`,
    })
  }

  for (const match of source.matchAll(/var\(\s*(--nv-[\w-]+)\s*(,)?/g)) {
    const [, token, fallbackMarker] = match
    if (definedNvTokens.has(token) || fallbackMarker) continue

    const before = source.slice(0, match.index)
    const line = before.split(/\r?\n/).length
    violations.push({
      file: relative(rootDir, file).replaceAll('\\', '/'),
      line,
      rule: 'undefined nv token',
      match: token,
      message: 'Define this nv token in the shared foundation or provide an explicit var() fallback.',
    })
  }
}

if (violations.length > 0) {
  console.error('Color token guard failed.')
  for (const violation of violations) {
    console.error(`- ${violation.file}:${violation.line} [${violation.rule}] ${violation.match}`)
    console.error(`  ${violation.message}`)
  }
  process.exit(1)
}

console.log(`Color token guard passed (light palette: ${themeBaseColorCounts.light}/${maxThemeBaseColors}, dark palette: ${themeBaseColorCounts.dark}/${maxThemeBaseColors}).`)
