import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, type Ref } from 'vue'
import { useAdminIpBlocksManager } from '../useAdminIpBlocksManager'
import { apiSuccessResponse, pageResponseFixture } from '@/test/apiResponseFixtures'
import type { IpBlock, PageResponse } from '@/types'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  blockIp: vi.fn(),
  confirm: vi.fn(),
  ipBlocksData: undefined as Ref<PageResponse<IpBlock> | null> | undefined,
  unblockIp: vi.fn(),
  useIpBlocksParams: undefined as Ref<{ page: number, size: number }> | undefined,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, string>) => (params?.ip ? `${key}:${params.ip}` : key),
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

vi.mock('@/composables/useAdmin', async () => {
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  mocks.ipBlocksData = ref<PageResponse<IpBlock> | null>(null)

  return {
    useAdmin: () => ({
      useIpBlocks: (params: Ref<{ page: number, size: number }>) => {
        mocks.useIpBlocksParams = params
        return { data: mocks.ipBlocksData }
      },
      useBlockIp: () => ({
        mutateAsync: mocks.blockIp,
      }),
      useUnblockIp: () => ({
        mutateAsync: mocks.unblockIp,
      }),
    }),
  }
})

const ipBlock = (ipAddress = '192.168.0.1'): IpBlock => ({
  ipAddress,
  reason: 'Spam',
  startDate: '2026-01-01T00:00:00',
  admin: {
    adminId: 1,
    displayName: 'Admin',
  },
})

const pageResponse = (
  content: IpBlock[],
  overrides: Partial<PageResponse<IpBlock>> = {},
): PageResponse<IpBlock> => pageResponseFixture(content, { size: 20, totalPages: 1, ...overrides })

describe('useAdminIpBlocksManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
    mocks.blockIp.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
    mocks.unblockIp.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
    mocks.ipBlocksData!.value = pageResponse([ipBlock()])
    mocks.useIpBlocksParams = undefined
  })

  it('exposes fixed page parameters for the IP block query', () => {
    const manager = useAdminIpBlocksManager()

    expect(mocks.useIpBlocksParams?.value).toEqual({ page: 0, size: 20 })

    manager.page.value = 2

    expect(mocks.useIpBlocksParams?.value).toEqual({ page: 2, size: 20 })
  })

  it('blocks an IP and resets the form after success', async () => {
    const manager = useAdminIpBlocksManager()
    manager.page.value = 3
    manager.newIp.value = '  10.0.0.1  '
    manager.blockReason.value = '  Abuse  '

    await manager.handleBlockIp()

    expect(mocks.blockIp).toHaveBeenCalledWith({ ipAddress: '10.0.0.1', reason: 'Abuse' })
    expect(manager.page.value).toBe(0)
    expect(manager.newIp.value).toBe('')
    expect(manager.blockReason.value).toBe('')
    expect(mocks.addToast).toHaveBeenCalledWith('admin.security.messages.blocked', 'success')
  })

  it('does not block when the IP or reason field is empty after trimming', async () => {
    const manager = useAdminIpBlocksManager()

    manager.newIp.value = ''
    manager.blockReason.value = 'Abuse'
    await manager.handleBlockIp()

    manager.newIp.value = '10.0.0.1'
    manager.blockReason.value = ''
    await manager.handleBlockIp()

    manager.newIp.value = '   '
    manager.blockReason.value = 'Abuse'
    await manager.handleBlockIp()

    manager.newIp.value = '10.0.0.1'
    manager.blockReason.value = '   '
    await manager.handleBlockIp()

    expect(mocks.blockIp).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('keeps form values and toast quiet when blocking fails', async () => {
    const manager = useAdminIpBlocksManager()
    const error = new Error('blocked')
    mocks.blockIp.mockRejectedValueOnce(error)
    manager.newIp.value = '10.0.0.1'
    manager.blockReason.value = 'Abuse'

    await manager.handleBlockIp()

    expect(manager.newIp.value).toBe('10.0.0.1')
    expect(manager.blockReason.value).toBe('Abuse')
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('confirms before unblocking an IP', async () => {
    const manager = useAdminIpBlocksManager()

    await manager.handleUnblockIp('192.168.0.1')

    expect(mocks.confirm).toHaveBeenCalledWith('admin.security.messages.confirmUnblock:192.168.0.1')
    expect(mocks.unblockIp).toHaveBeenCalledWith('192.168.0.1')
    expect(mocks.addToast).toHaveBeenCalledWith('admin.security.messages.unblocked', 'success')
  })

  it('skips unblocking when confirmation is cancelled', async () => {
    const manager = useAdminIpBlocksManager()
    mocks.confirm.mockResolvedValueOnce(false)

    await manager.handleUnblockIp('192.168.0.1')

    expect(mocks.unblockIp).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('moves to the previous page after unblocking the only item on a later page', async () => {
    const manager = useAdminIpBlocksManager()
    manager.page.value = 2
    mocks.ipBlocksData!.value = pageResponse([ipBlock()], { number: 2, totalPages: 3 })

    await manager.handleUnblockIp('192.168.0.1')

    expect(manager.page.value).toBe(1)
  })

  it('keeps the current page when unblocking from the first page or a multi-item page', async () => {
    const manager = useAdminIpBlocksManager()
    manager.page.value = 0
    mocks.ipBlocksData!.value = pageResponse([ipBlock()], { number: 0, totalPages: 2 })

    await manager.handleUnblockIp('192.168.0.1')

    expect(manager.page.value).toBe(0)

    manager.page.value = 2
    mocks.ipBlocksData!.value = pageResponse([ipBlock('192.168.0.1'), ipBlock('192.168.0.2')], {
      number: 2,
      totalPages: 3,
    })

    await manager.handleUnblockIp('192.168.0.2')

    expect(manager.page.value).toBe(2)
  })

  it('keeps page and toast quiet when unblocking fails', async () => {
    const manager = useAdminIpBlocksManager()
    mocks.unblockIp.mockRejectedValueOnce(new Error('failed'))
    manager.page.value = 2
    mocks.ipBlocksData!.value = pageResponse([ipBlock()], { number: 2, totalPages: 3 })

    await manager.handleUnblockIp('192.168.0.1')

    expect(manager.page.value).toBe(2)
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('corrects the current page when total pages shrink', async () => {
    const manager = useAdminIpBlocksManager()
    manager.page.value = 4

    mocks.ipBlocksData!.value = pageResponse([], { totalPages: 0, number: 0 })
    await nextTick()

    expect(manager.page.value).toBe(0)

    manager.page.value = 4
    mocks.ipBlocksData!.value = pageResponse([ipBlock()], { totalPages: 3, number: 2 })
    await nextTick()

    expect(manager.page.value).toBe(2)
  })

  it('opens and closes the detail modal while preserving selected IP block', () => {
    const manager = useAdminIpBlocksManager()
    const selected = ipBlock('10.0.0.1')

    manager.openDetailModal(selected)
    expect(manager.isDetailModalOpen.value).toBe(true)
    expect(manager.selectedIpBlock.value).toEqual(selected)

    manager.closeDetailModal()

    expect(manager.isDetailModalOpen.value).toBe(false)
    expect(manager.selectedIpBlock.value).toEqual(selected)
  })
})
