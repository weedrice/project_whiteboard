import { readdir, readFile } from 'node:fs/promises'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const viewsRoot = fileURLToPath(new URL('../src/views', import.meta.url))
const fixedInlineDimension = /style\s*=\s*"[^"]*(?:width|height)\s*:\s*\d+(?:\.\d+)?px/i

async function collectVueFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return collectVueFiles(path)
    return extname(entry.name) === '.vue' ? [path] : []
  }))

  return nested.flat()
}

const violations = []
for (const file of await collectVueFiles(viewsRoot)) {
  const source = await readFile(file, 'utf8')
  const lines = source.split(/\r?\n/)

  lines.forEach((line, index) => {
    if (!fixedInlineDimension.test(line)) return
    violations.push(`${relative(viewsRoot, file)}:${index + 1} ${line.trim()}`)
  })
}

if (violations.length > 0) {
  console.error('Responsive UI guard found fixed inline pixel dimensions in route views:')
  violations.forEach((violation) => console.error(`- ${violation}`))
  process.exit(1)
}

console.log('Responsive UI guard passed.')
