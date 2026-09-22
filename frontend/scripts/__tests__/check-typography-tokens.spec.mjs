import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { afterEach, describe, expect, it } from 'vitest'
import { checkTypographyTokenContracts } from '../check-typography-tokens.mjs'

const temporaryDirectories = []

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => (
    rm(directory, { recursive: true, force: true })
  )))
})

async function createSourceFixture(files) {
  const directory = await mkdtemp(join(tmpdir(), 'noviis-typography-contract-'))
  temporaryDirectories.push(directory)
  await Promise.all(Object.entries(files).map(async ([name, source]) => {
    const path = join(directory, name)
    await mkdir(join(path, '..'), { recursive: true })
    await writeFile(path, source, 'utf8')
  }))
  return directory
}

const foundation = `@theme {
  --text-xs: 0.75rem;
  --text-compact: 0.8125rem;
}
:root {
  --nv-type-kicker: var(--text-xs);
}`

describe('typography token guard', () => {
  it('accepts shared token utilities, token declarations, and documented relative-size exceptions', async () => {
    const sourceDir = await createSourceFixture({
      'styles/foundation.css': foundation,
      'Valid.vue': `<template><p class="text-compact">Copy</p></template>
<style scoped>
.copy { font-size: var(--text-xs); }
.code {
  /* typography-guard-allow: code follows the surrounding user-authored text */
  font-size: 0.9em;
}
</style>`,
    })

    await expect(checkTypographyTokenContracts(
      sourceDir,
      join(sourceDir, 'styles', 'foundation.css'),
    )).resolves.toEqual([])
  })

  it('rejects literal, arbitrary, and undefined font-size values', async () => {
    const sourceDir = await createSourceFixture({
      'styles/foundation.css': foundation,
      'Invalid.vue': `<template><p class="sm:text-[13px]">Copy</p></template>
<style scoped>
.literal { font-size: 0.8125rem; }
.undefined { font-size: var(--text-unknown); }
</style>`,
      'generated-media.mjs': 'export const style = { fontSize: 22 }',
    })

    const violations = await checkTypographyTokenContracts(
      sourceDir,
      join(sourceDir, 'styles', 'foundation.css'),
    )

    expect(violations).toEqual(expect.arrayContaining([
      expect.objectContaining({ rule: 'literal font size', line: 3 }),
      expect.objectContaining({ rule: 'undefined typography token', line: 4 }),
      expect.objectContaining({ rule: 'arbitrary text size', line: 1 }),
      expect.objectContaining({ rule: 'literal generated-media font size', line: 1 }),
    ]))
  })
})
