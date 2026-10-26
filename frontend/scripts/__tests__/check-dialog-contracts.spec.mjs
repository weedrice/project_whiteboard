import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import { checkDialogContracts } from '../check-dialog-contracts.mjs'

const fixtures = []

afterEach(async () => {
  // Only directories created by this fixture are removed.
  await Promise.all(fixtures.splice(0).map((directory) => rm(directory, { recursive: true, force: true })))
})

async function checkFixture(source) {
  const directory = await mkdtemp(join(tmpdir(), 'noviis-dialog-contract-'))
  fixtures.push(directory)
  await writeFile(join(directory, 'Example.vue'), source, 'utf8')
  return checkDialogContracts(directory)
}

const exampleScript = `<script setup>
const example = '<template><div role="dialog">Example</div></template>'
</script>`

describe('dialog contract guard', () => {
  it('ignores template markup in script strings and comments', async () => {
    expect(await checkFixture(`${exampleScript}
<!-- <template><div role="dialog"></div></template> -->
<template><pre>{{ example }}</pre></template>`)).toEqual([])
  })

  it('still checks the real template and reports its source line', async () => {
    const violations = await checkFixture(`${exampleScript}
<template>
  <div role="dialog" />
</template>`)
    expect(violations).toHaveLength(3)
    expect(violations.every((violation) => violation.startsWith('Example.vue:5 '))).toBe(true)
    expect(violations.some((violation) => violation.includes('surface'))).toBe(true)
    expect(violations.some((violation) => violation.includes('aria-modal'))).toBe(true)
  })

  it('fails closed on malformed Vue source', async () => {
    const violations = await checkFixture('<template><div></template>')
    expect(violations).toHaveLength(1)
    expect(violations[0]).toContain('Vue template AST')
  })

  it('allows a script-only component without treating strings as UI', async () => {
    expect(await checkFixture(exampleScript)).toEqual([])
  })
})
