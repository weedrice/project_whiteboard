import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { afterEach, describe, expect, it } from 'vitest'
import {
  checkUiPrimitiveContracts,
  findRetiredBoundClasses,
  findRetiredCssSelectors,
  findRetiredSpreadClasses,
} from '../check-ui-primitives.mjs'

const temporaryDirectories = []

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => (
    rm(directory, { recursive: true, force: true })
  )))
})

async function createSourceFixture(files) {
  const directory = await mkdtemp(join(tmpdir(), 'noviis-ui-contract-'))
  temporaryDirectories.push(directory)
  await Promise.all(Object.entries(files).map(async ([name, source]) => {
    const path = join(directory, name)
    await mkdir(join(path, '..'), { recursive: true })
    await writeFile(path, source, 'utf8')
  }))
  return directory
}

describe('UI primitive contract guard', () => {
  it('detects retired classes in string and object class bindings without matching model properties', () => {
    expect(findRetiredBoundClasses("active ? 'card' : 'nv-surface'")).toEqual(['card'])
    expect(findRetiredBoundClasses('{ "table-container": scrollable }')).toEqual(['table-container'])
    expect(findRetiredBoundClasses('badge.representative ? selectedClass : undefined')).toEqual([])
    expect(findRetiredSpreadClasses("{ class: active ? 'card-body' : 'nv-surface', id: 'panel' }")).toEqual(['card-body'])
  })

  it('detects retired CSS selectors without matching namespaced selectors', () => {
    expect(findRetiredCssSelectors('.card { padding: 1rem; }')).toEqual(['card'])
    expect(findRetiredCssSelectors('.auth-card { padding: 1rem; }')).toEqual([])
  })

  it('checks legacy files, imports, primitive ownership, and native classifications outside route views', async () => {
    const fixture = await createSourceFixture({
      'feature.ts': "import './legacy-components.css'",
      'legacy-components.css': '.nv-safe { display: block; }',
      'Uploader.vue': '<template><input type="file"></template>',
      'ValidUploader.vue': '<template><input type="file" data-ui-native="file"></template>',
      'WrongClassification.vue': '<template><textarea data-ui-native="file"></textarea></template>',
      'ValidChoice.vue': `<template><input :type="multiple ? 'checkbox' : 'radio'" data-ui-native="choice"></template><script setup>defineProps({ multiple: Boolean })</script>`,
      'SpreadClass.vue': `<template><div v-bind="{ class: 'card-header' }" /></template>`,
      'ScriptClass.vue': `<script setup>const shellClass = 'table-row'</script><template><div :class="shellClass" /></template>`,
      'DirectPrimitiveClass.vue': '<template><a href="/" class="btn-secondary">Home</a></template>',
      'styles.css': '.list-group { display: grid; }',
    })

    const violations = await checkUiPrimitiveContracts(fixture)

    expect(violations).toEqual(expect.arrayContaining([
      expect.stringContaining('feature.ts: retired legacy-components.css reference'),
      expect.stringContaining('legacy-components.css: retired legacy-components.css file'),
      expect.stringContaining('Uploader.vue:1 raw <input> requires data-ui-native'),
      expect.stringContaining('WrongClassification.vue:1 data-ui-native="file" does not match <textarea> type'),
      expect.stringContaining('SpreadClass.vue:1 retired UI class: card-header'),
      expect.stringContaining('ScriptClass.vue: retired UI class in script: table-row'),
      expect.stringContaining('DirectPrimitiveClass.vue:1 shared primitive class must be owned by a Base component: btn-secondary'),
      expect.stringContaining('styles.css: retired UI selector: list-group'),
    ]))
    expect(violations.some((violation) => violation.includes('ValidUploader.vue'))).toBe(false)
    expect(violations.some((violation) => violation.includes('ValidChoice.vue'))).toBe(false)
  })
})
