import { nextTick, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { useConfigEditor } from '../useConfigEditor'
import type { GlobalConfig } from '@/types'

function config(key: string, value: string, description = ''): GlobalConfig {
  return { key, value, description }
}

describe('useConfigEditor', () => {
  it('exposes drafts from incoming config data', () => {
    const configsData = ref<GlobalConfig[]>([
      config('site.name', 'Noviis', 'Service name'),
    ])
    const editor = useConfigEditor(configsData)

    expect(editor.hasUnsavedChanges.value).toBe(false)

    expect(editor.configs.value).toEqual([
      { key: 'site.name', value: 'Noviis', description: 'Service name' },
    ])
    expect(editor.getDraft('site.name')).toEqual({
      key: 'site.name',
      value: 'Noviis',
      description: 'Service name',
    })
  })

  it('updates only known drafts', () => {
    const configsData = ref<GlobalConfig[]>([
      config('site.name', 'Noviis', 'Service name'),
    ])
    const editor = useConfigEditor(configsData)

    editor.updateDraft('missing.key', { value: 'Ignored' })
    editor.updateDraft('site.name', { value: 'Updated' })

    expect(editor.getDraft('missing.key')).toBeUndefined()
    expect(editor.getDraft('site.name')).toEqual({
      key: 'site.name',
      value: 'Updated',
      description: 'Service name',
    })
    expect(editor.hasUnsavedChanges.value).toBe(true)
  })

  it('preserves unsaved drafts when server data refreshes', async () => {
    const configsData = ref<GlobalConfig[]>([
      config('site.name', 'Noviis', 'Service name'),
    ])
    const editor = useConfigEditor(configsData)
    editor.updateDraft('site.name', { value: 'Draft value' })

    configsData.value = [
      config('site.name', 'Server value', 'Server description'),
    ]
    await nextTick()

    expect(editor.getDraft('site.name')).toEqual({
      key: 'site.name',
      value: 'Draft value',
      description: 'Service name',
    })
  })

  it('replaces unchanged drafts with refreshed server data', async () => {
    const configsData = ref<GlobalConfig[]>([
      config('site.name', 'Noviis', 'Service name'),
    ])
    const editor = useConfigEditor(configsData)

    configsData.value = [
      config('site.name', 'Server value', 'Server description'),
    ]
    await nextTick()

    expect(editor.getDraft('site.name')).toEqual({
      key: 'site.name',
      value: 'Server value',
      description: 'Server description',
    })
  })
})
