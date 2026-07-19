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

vi.mock('@/api/commonCode', () => ({ commonCodeApi: commonCodeApiMock }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: vi.fn().mockResolvedValue(true) }) }))

const response = (data: unknown) => ({ data: { success: true, data } })

describe('CommonCodeManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    commonCodeApiMock.getAll.mockResolvedValue(response([
      { typeCode: 'REPORT_REASON', typeName: 'Report reason', description: 'Reasons' },
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
})
