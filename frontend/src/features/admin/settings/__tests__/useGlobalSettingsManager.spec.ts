import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { Ref } from 'vue'
import { useGlobalSettingsManager } from '../useGlobalSettingsManager'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
import { useAuthStore } from '@/stores/auth'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  confirm: vi.fn(),
  configsData: {
    __v_isRef: true,
    value: [
      {
        key: 'site.name',
        value: 'Noviis',
        description: 'Service name',
      },
    ] as Array<{ key: string; value: string; description?: string }>,
  } as unknown as Ref<Array<{ key: string; value: string; description?: string }>>,
  createConfig: vi.fn(),
  deleteConfig: vi.fn(),
  isLoading: { __v_isRef: true, value: false } as unknown as Ref<boolean>,
  updateConfig: vi.fn(),
  refreshImagePolicy: vi.fn(),
}))

vi.mock('@/features/emoticon/form/useEmoticonImagePolicy', () => ({
  EMOTICON_IMAGE_MAX_COUNT_KEY: 'EMOTICON_IMAGE_MAX_COUNT',
  useEmoticonImagePolicy: () => ({
    refresh: mocks.refreshImagePolicy,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: mocks.confirm,
  }),
}))

vi.mock('@/features/admin/useAdmin', async () => {
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  mocks.configsData = ref(mocks.configsData.value)
  mocks.isLoading = ref(false)
  return {
    useAdmin: () => ({
    useConfigs: () => ({
      data: mocks.configsData,
      isLoading: mocks.isLoading,
    }),
    useUpdateConfig: () => ({
      mutateAsync: mocks.updateConfig,
    }),
    useCreateConfig: () => ({
      mutateAsync: mocks.createConfig,
    }),
    useDeleteConfig: () => ({
      mutateAsync: mocks.deleteConfig,
    }),
    }),
  }
})

describe('useGlobalSettingsManager', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mocks.configsData.value = [
      {
        key: 'site.name',
        value: 'Noviis',
        description: 'Service name',
      },
    ]
    mocks.confirm.mockResolvedValue(true)
    mocks.createConfig.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
    mocks.deleteConfig.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
    mocks.updateConfig.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
  })

  it('saves the current draft and shows a success toast', async () => {
    const manager = useGlobalSettingsManager()

    manager.updateDraft('site.name', { value: '  Updated  ', description: '  Updated description  ' })
    await manager.handleSave('site.name')

    expect(mocks.updateConfig).toHaveBeenCalledWith({
      key: 'site.name',
      value: 'Updated',
      description: 'Updated description',
    })
    expect(mocks.addToast).toHaveBeenCalledWith('admin.settings.messages.saved', 'success')
  })

  it('refreshes the public policy after saving the emoticon image limit', async () => {
    mocks.configsData.value = [{
      key: 'EMOTICON_IMAGE_MAX_COUNT',
      value: '20',
      description: 'limit',
    }]
    const manager = useGlobalSettingsManager()

    manager.updateDraft('EMOTICON_IMAGE_MAX_COUNT', { value: '30' })
    await manager.handleSave('EMOTICON_IMAGE_MAX_COUNT')

    expect(mocks.refreshImagePolicy).toHaveBeenCalledTimes(1)
  })

  it('blocks repeated saves for the same key until its request settles', async () => {
    const update = createDeferred<unknown>()
    mocks.updateConfig.mockReturnValueOnce(update.promise)
    const manager = useGlobalSettingsManager()
    manager.updateDraft('site.name', { value: 'Updated' })

    const firstSave = manager.handleSave('site.name')
    const secondSave = manager.handleSave('site.name')

    expect(manager.isSavePending('site.name')).toBe(true)
    expect(mocks.updateConfig).toHaveBeenCalledTimes(1)

    update.resolve(undefined)
    await Promise.all([firstSave, secondSave])
    expect(manager.isSavePending('site.name')).toBe(false)
  })

  it('ignores an existing-config save response from an older authentication session', async () => {
    const update = createDeferred<unknown>()
    mocks.updateConfig.mockReturnValueOnce(update.promise)
    const manager = useGlobalSettingsManager()
    const authStore = useAuthStore()
    manager.updateDraft('site.name', { value: 'Updated' })
    const pending = manager.handleSave('site.name')

    authStore.sessionGeneration += 1
    update.resolve(undefined)
    await pending

    expect(mocks.refreshImagePolicy).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalledWith('admin.settings.messages.saved', 'success')
  })

  it('skips saving when the draft value is blank after trimming', async () => {
    const manager = useGlobalSettingsManager()

    manager.updateDraft('site.name', { value: '   ', description: 'Updated description' })
    await manager.handleSave('site.name')

    expect(mocks.updateConfig).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('skips saving when no draft exists for the key', async () => {
    const manager = useGlobalSettingsManager()

    await manager.handleSave('missing.key')

    expect(mocks.updateConfig).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('creates a config and resets the modal form after success', async () => {
    const manager = useGlobalSettingsManager()
    manager.openCreateModal()
    Object.assign(manager.newConfig, {
      key: '  site.description  ',
      value: '  Community  ',
      description: '  Description  ',
    })

    await manager.handleCreateConfig()

    expect(mocks.createConfig).toHaveBeenCalledWith({
      key: 'site.description',
      value: 'Community',
      description: 'Description',
    })
    expect(manager.isModalOpen.value).toBe(false)
    expect(manager.newConfig).toEqual({ key: '', value: '', description: '' })
    expect(mocks.addToast).toHaveBeenCalledWith('admin.settings.messages.saved', 'success')
  })

  it('does not create when key or value is empty', async () => {
    const manager = useGlobalSettingsManager()

    manager.newConfig.key = ''
    manager.newConfig.value = 'value'
    await manager.handleCreateConfig()

    manager.newConfig.key = 'site.description'
    manager.newConfig.value = ''
    await manager.handleCreateConfig()

    manager.newConfig.key = '   '
    manager.newConfig.value = 'value'
    await manager.handleCreateConfig()

    manager.newConfig.key = 'site.description'
    manager.newConfig.value = '   '
    await manager.handleCreateConfig()

    expect(mocks.createConfig).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('confirms before deleting a config', async () => {
    const manager = useGlobalSettingsManager()

    await manager.handleDelete('site.name')

    expect(mocks.confirm).toHaveBeenCalledWith('common.confirmDelete')
    expect(mocks.deleteConfig).toHaveBeenCalledWith('site.name')
    expect(mocks.addToast).toHaveBeenCalledWith('common.deleted', 'success')
  })

  it('skips deletion when confirmation is cancelled', async () => {
    const manager = useGlobalSettingsManager()
    mocks.confirm.mockResolvedValueOnce(false)

    await manager.handleDelete('site.name')

    expect(mocks.deleteConfig).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('keeps modal form values when creation fails', async () => {
    const manager = useGlobalSettingsManager()
    mocks.createConfig.mockRejectedValueOnce(new Error('failed'))
    manager.openCreateModal()
    Object.assign(manager.newConfig, {
      key: 'site.description',
      value: 'Community',
      description: 'Description',
    })

    await manager.handleCreateConfig()

    expect(manager.isModalOpen.value).toBe(true)
    expect(manager.newConfig).toEqual({
      key: 'site.description',
      value: 'Community',
      description: 'Description',
    })
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('skips deletion when the authentication session changes during confirmation', async () => {
    const confirmation = createDeferred<boolean>()
    mocks.confirm.mockReturnValueOnce(confirmation.promise)
    const manager = useGlobalSettingsManager()
    const authStore = useAuthStore()
    const pending = manager.handleDelete('site.name')

    authStore.sessionGeneration += 1
    confirmation.resolve(true)
    await pending

    expect(mocks.deleteConfig).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('skips deletion when the selected config key disappears during confirmation', async () => {
    const confirmation = createDeferred<boolean>()
    mocks.confirm.mockReturnValueOnce(confirmation.promise)
    const manager = useGlobalSettingsManager()
    const pending = manager.handleDelete('site.name')

    mocks.configsData.value.splice(0)
    confirmation.resolve(true)
    await pending

    expect(mocks.deleteConfig).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('does not let the create modal close or reopen while creation is pending', async () => {
    let resolveCreate!: (value: unknown) => void
    mocks.createConfig.mockReturnValueOnce(new Promise(resolve => { resolveCreate = resolve }))
    const manager = useGlobalSettingsManager()
    manager.openCreateModal()
    Object.assign(manager.newConfig, { key: 'old.key', value: 'old', description: '' })
    const pending = manager.handleCreateConfig()

    manager.closeCreateModal()
    manager.openCreateModal()
    expect(manager.isModalOpen.value).toBe(true)
    expect(manager.newConfig).toEqual({ key: 'old.key', value: 'old', description: '' })

    resolveCreate(undefined)
    await pending

    expect(manager.isModalOpen.value).toBe(false)
    expect(manager.newConfig).toEqual({ key: '', value: '', description: '' })
    expect(mocks.addToast).toHaveBeenCalledWith('admin.settings.messages.saved', 'success')
  })

  it('closes and clears the create modal at the authentication boundary', () => {
    const manager = useGlobalSettingsManager()
    const authStore = useAuthStore()
    manager.openCreateModal()
    Object.assign(manager.newConfig, { key: 'old.key', value: 'old', description: 'private draft' })

    authStore.sessionGeneration += 1

    expect(manager.isModalOpen.value).toBe(false)
    expect(manager.newConfig).toEqual({ key: '', value: '', description: '' })
  })
})
