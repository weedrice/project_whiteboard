import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = fileURLToPath(new URL('..', import.meta.url))
const srcDir = join(rootDir, 'src')
const extensions = new Set(['.vue', '.ts', '.css'])

const rules = [
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

const violations = []

for (const file of walk(srcDir)) {
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
}

if (violations.length > 0) {
  console.error('Color token guard failed.')
  for (const violation of violations) {
    console.error(`- ${violation.file}:${violation.line} [${violation.rule}] ${violation.match}`)
    console.error(`  ${violation.message}`)
  }
  process.exit(1)
}

console.log('Color token guard passed.')
