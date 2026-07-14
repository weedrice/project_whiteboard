import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useGlobalSettingsManager } from '../useGlobalSettingsManager'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'

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
  },
  createConfig: vi.fn(),
  deleteConfig: vi.fn(),
  isLoading: { __v_isRef: true, value: false },
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

vi.mock('@/composables/useAdmin', () => ({
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
}))

describe('useGlobalSettingsManager', () => {
  beforeEach(() => {
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
})
