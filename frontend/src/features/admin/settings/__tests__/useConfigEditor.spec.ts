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

  it.each(['before', 'after'])('acknowledges a normalized save when its refetch arrives %s the response', async (order) => {
    const configsData = ref([config('price', '100')])
    const editor = useConfigEditor(configsData)
    editor.updateDraft('price', { value: '  200  ', description: '  Updated  ' })
    const submission = editor.captureSave('price')!
    const saved = config('price', '200', 'Updated')

    if (order === 'before') {
      configsData.value = [saved]
      await nextTick()
    }
    submission.acknowledge(saved)
    if (order === 'after') {
      configsData.value = [saved]
      await nextTick()
    }

    expect(editor.getDraft('price')).toEqual(saved)
    expect(editor.hasUnsavedChanges.value).toBe(false)
  })

  it('preserves a newer editing revision even if it returns to the submitted text', () => {
    const editor = useConfigEditor(ref([config('price', '100')]))
    editor.updateDraft('price', { value: '  200  ' })
    const submission = editor.captureSave('price')!
    editor.updateDraft('price', { value: '300' })
    editor.updateDraft('price', { value: '  200  ' })

    submission.acknowledge(config('price', '200'))

    expect(editor.getDraft('price')?.value).toBe('  200  ')
    expect(editor.hasUnsavedChanges.value).toBe(true)
    editor.updateDraft('price', { value: '200' })
    expect(editor.hasUnsavedChanges.value).toBe(false)
  })

  it('does not acknowledge a draft from before a reset', async () => {
    const configsData = ref([config('price', '100')])
    const editor = useConfigEditor(configsData)
    const submission = editor.captureSave('price')!
    editor.resetDrafts()
    configsData.value = [config('price', '300')]
    await nextTick()

    submission.acknowledge(config('price', '200'))

    expect(editor.getDraft('price')?.value).toBe('300')
    expect(editor.hasUnsavedChanges.value).toBe(false)
    expect(editor.captureSave('missing')).toBeNull()
  })
})
