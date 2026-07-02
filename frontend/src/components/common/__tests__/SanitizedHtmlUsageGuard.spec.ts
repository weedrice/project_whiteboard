import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'

const sourceRoot = join(process.cwd(), 'src')
const allowedRawHtmlRenderers = new Set([
  'components/common/SanitizedHtmlView.vue',
])

function toSourcePath(filePath: string) {
  return relative(sourceRoot, filePath).split(sep).join('/')
}

function collectVueFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const fullPath = join(directory, entry)
    if (statSync(fullPath).isDirectory()) {
      return collectVueFiles(fullPath)
    }
    return fullPath.endsWith('.vue') ? [fullPath] : []
  })
}

describe('SanitizedHtmlView usage guard', () => {
  it('keeps raw html rendering behind the shared sanitized renderer', () => {
    const rawHtmlRendererPattern = /\bv-html\s*=|:innerHTML\s*=|\bv-bind:innerHTML\s*=/
    const violations = collectVueFiles(sourceRoot)
      .filter((filePath) => !toSourcePath(filePath).includes('/__tests__/'))
      .filter((filePath) => rawHtmlRendererPattern.test(readFileSync(filePath, 'utf8')))
      .map(toSourcePath)
      .filter((sourcePath) => !allowedRawHtmlRenderers.has(sourcePath))

    expect(violations).toEqual([])
  })
})
