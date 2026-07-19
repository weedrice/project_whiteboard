import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommonCodeManagement from '../CommonCodeManagement.vue'

const commonCodeApiMock = vi.hoisted(() => ({
  getAll: vi.fn(),
  getDetails: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  createDetail: vi.fn(),
  updateDetail: vi.fn(),
  deleteDetail: vi.fn(),
}))

const navigationMocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  routeLeaveGuard: null as null | (() => Promise<boolean>),
}))

vi.mock('@/api/commonCode', () => ({ commonCodeApi: commonCodeApiMock }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: navigationMocks.confirm }) }))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    onBeforeRouteLeave: (guard: () => Promise<boolean>) => {
      navigationMocks.routeLeaveGuard = guard
    },
  }
})

const response = (data: unknown) => ({ data: { success: true, data } })

describe('CommonCodeManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    navigationMocks.confirm.mockResolvedValue(true)
    navigationMocks.routeLeaveGuard = null
    commonCodeApiMock.getAll.mockResolvedValue(response([
      { typeCode: 'REPORT_REASON', typeName: 'Report reason', description: 'Reasons' },
      { typeCode: 'USER_STATUS', typeName: 'User status', description: 'Statuses' },
    ]))
    commonCodeApiMock.getDetails.mockResolvedValue(response([
      { id: 1, typeCode: 'REPORT_REASON', codeValue: 'SPAM', codeName: 'Spam', sortOrder: 1, isActive: true },
    ]))
  })

  it('loads code groups and the selected group details', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    expect(commonCodeApiMock.getAll).toHaveBeenCalledOnce()
    expect(commonCodeApiMock.getDetails).toHaveBeenCalledWith('REPORT_REASON', { signal: expect.any(AbortSignal) })
    expect(wrapper.text()).toContain('Report reason')
    expect(wrapper.text()).toContain('Spam')
  })

  it('keeps the selected code editor intact after cancelling code creation', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    const masterInputs = wrapper.findAll('input')
    expect(masterInputs[0].element.value).toBe('REPORT_REASON')
    expect(masterInputs[1].element.value).toBe('Report reason')
    expect(masterInputs[2].element.value).toBe('Reasons')

    const addButton = wrapper.findAll('button').find((button) => button.text().includes('admin.commonCodes.addCode'))
    await addButton?.trigger('click')
    const cancelButton = wrapper.findAll('button').find((button) => button.text() === 'common.cancel')
    await cancelButton?.trigger('click')

    expect(wrapper.findAll('input')[0].element.value).toBe('REPORT_REASON')
    expect(wrapper.findAll('input')[1].element.value).toBe('Report reason')
    expect(wrapper.findAll('input')[2].element.value).toBe('Reasons')
  })

  it('keeps the current code selected when discarding dirty master changes is cancelled', async () => {
    navigationMocks.confirm.mockResolvedValueOnce(false)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    await wrapper.findAll('input')[1].setValue('Changed name')
    const otherCode = wrapper.findAll('button').find((button) => button.text().includes('USER_STATUS'))
    await otherCode?.trigger('click')
    await flushPromises()

    expect(navigationMocks.confirm).toHaveBeenCalledWith('admin.commonCodes.messages.confirmDiscardChanges')
    expect(wrapper.findAll('input')[0].element.value).toBe('REPORT_REASON')
    expect(wrapper.findAll('input')[1].element.value).toBe('Changed name')
    expect(commonCodeApiMock.getDetails).not.toHaveBeenCalledWith('USER_STATUS', expect.anything())
  })

  it('blocks route leave when dirty master changes are not confirmed', async () => {
    navigationMocks.confirm.mockResolvedValueOnce(false)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    await wrapper.findAll('input')[1].setValue('Changed name')

    await expect(navigationMocks.routeLeaveGuard?.()).resolves.toBe(false)
    expect(navigationMocks.confirm).toHaveBeenCalledWith('admin.commonCodes.messages.confirmDiscardChanges')
  })
})
