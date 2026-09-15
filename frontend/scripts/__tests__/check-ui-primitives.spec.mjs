import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { afterEach, describe, expect, it } from 'vitest'
import {
  checkUiPrimitiveContracts,
  findRetiredCssSelectors,
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
      'BoundLiteralClass.vue': `<script setup>const active = true</script><template><div :class="active ? 'card' : 'nv-surface'" /></template>`,
      'BoundObjectClass.vue': `<script setup>const active = true</script><template><div :class="{ 'table-container': active }" /></template>`,
      'ValidModelProperty.vue': `<script setup>
defineProps({ badge: Object })
const selectedClass = 'nv-selected'
</script><template><div :class="badge.representative ? selectedClass : undefined" /></template>`,
      'SpreadClass.vue': `<template><div v-bind="{ class: 'card-header' }" /></template>`,
      'ScriptClass.vue': `<script setup>const shellClass = 'table-row'</script><template><div :class="shellClass" /></template>`,
      'NamedSpreadClass.vue': `<script setup>
const retiredToken = 'card-body'
const attrs = {
  class: retiredToken,
}
</script><template><div v-bind="attrs" /></template>`,
      'NamedBoundClass.vue': `<script setup>
const styling = 'table-row'
</script><template><div :class="styling" /></template>`,
      'MultilineClass.vue': `<script setup>
import { computed } from 'vue'
const buttonClass = computed(() => [
  'btn-danger',
])
</script><template><button type="button" :class="buttonClass">Delete</button></template>`,
      'ExternalClasses.ts': `export const actionClass = [
  'btn-ghost',
]`,
      'DirectPrimitiveClass.vue': '<template><a href="/" class="btn-secondary">Home</a></template>',
      'ValidBusinessCopy.ts': "export const classificationMessage = 'card classification changed'",
      'styles.css': '.list-group { display: grid; }',
    })

    const violations = await checkUiPrimitiveContracts(fixture)

    expect(violations).toEqual(expect.arrayContaining([
      expect.stringContaining('feature.ts: retired legacy-components.css reference'),
      expect.stringContaining('legacy-components.css: retired legacy-components.css file'),
      expect.stringContaining('Uploader.vue:1 raw <input> requires data-ui-native'),
      expect.stringContaining('WrongClassification.vue:1 data-ui-native="file" does not match <textarea> type'),
      expect.stringContaining('BoundLiteralClass.vue:1 retired UI class: card'),
      expect.stringContaining('BoundObjectClass.vue:1 retired UI class: table-container'),
      expect.stringContaining('SpreadClass.vue:1 retired UI class: card-header'),
      expect.stringContaining('ScriptClass.vue: retired UI class in script: table-row'),
      expect.stringContaining('NamedSpreadClass.vue: retired UI class in script: card-body'),
      expect.stringContaining('NamedBoundClass.vue:3 retired UI class: table-row'),
      expect.stringContaining('MultilineClass.vue: shared primitive class in script: btn-danger'),
      expect.stringContaining('ExternalClasses.ts: shared primitive class in script: btn-ghost'),
      expect.stringContaining('DirectPrimitiveClass.vue:1 shared primitive class must be owned by a Base component: btn-secondary'),
      expect.stringContaining('styles.css: retired UI selector: list-group'),
    ]))
    expect(violations.some((violation) => violation.includes('ValidUploader.vue'))).toBe(false)
    expect(violations.some((violation) => violation.includes('ValidChoice.vue'))).toBe(false)
    expect(violations.some((violation) => violation.includes('ValidModelProperty.vue'))).toBe(false)
    expect(violations.some((violation) => violation.includes('ValidBusinessCopy.ts'))).toBe(false)
  })
})
