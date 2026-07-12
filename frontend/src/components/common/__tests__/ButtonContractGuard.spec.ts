import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const sourcePaths = [
  'src/views/emoticon/EmoticonDetail.vue',
  'src/views/emoticon/EmoticonEdit.vue',
  'src/views/emoticon/EmoticonRegister.vue',
  'src/components/common/KeyboardShortcutsModal.vue',
  'src/components/layout/BoardDropdown.vue',
]

describe('raw button contract', () => {
  it.each(sourcePaths)('declares a safe default type for buttons in %s', (path) => {
    const source = readFileSync(resolve(process.cwd(), path), 'utf8')
    const buttons = source.match(/<button\b[^>]*>/gs) ?? []

    expect(buttons.length).toBeGreaterThan(0)
    buttons.forEach((button) => expect(button).toContain('type="button"'))
  })
})
