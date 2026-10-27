import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { clearAuthScopedQueries, configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'
import { pageResponseFixture } from '@/test/apiResponseFixtures'
import { useReportModerationPage } from '../useReportModerationPage'
import { useAdminIpBlocksManager } from '@/features/admin/security/useAdminIpBlocksManager'
import type { IpBlock, Report, User } from '@/types'

vi.mock('@/router', () => ({ default: { push: vi.fn() } }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: vi.fn(), confirmWithReason: vi.fn() }),
}))

const report: Report = {
  reportId: 1,
  reporterId: 10,
  reporterDisplayName: 'Reporter',
  targetId: 20,
  targetType: 'POST',
  targetUserId: 20,
  targetDisplayName: 'Target',
  targetLoginId: 'target',
  reasonType: 'SPAM',
  contents: 'spam',
  remark: '',
  status: 'PENDING',
  createdAt: '2026-01-01T00:00:00',
}
const ipBlock: IpBlock = {
  ipAddress: '192.0.2.1',
  reason: 'Spam',
  startDate: '2026-01-01T00:00:00',
  admin: { adminId: 1, displayName: 'Admin' },
}

const screens = [
  {
    name: 'reports',
    url: '/admin/reports',
    item: report,
    pageAfterSessionChange: 1,
    setup() {
      const feature = useReportModerationPage()
      return {
        ...feature,
        items: feature.reports,
        selectDetail: () => feature.openDetailModal(report),
        selectedItem: feature.selectedReport,
      }
    },
  },
  {
    name: 'IP blocks',
    url: '/admin/ip-blocks',
    item: ipBlock,
    pageAfterSessionChange: 0,
    setup() {
      const feature = useAdminIpBlocksManager()
      return {
        ...feature,
        items: feature.ipBlocks,
        selectDetail: () => feature.openDetailModal(ipBlock),
        selectedItem: feature.selectedIpBlock,
      }
    },
  },
]

let pinia: ReturnType<typeof createPinia>
let auth: ReturnType<typeof useAuthStore>
let client: QueryClient
let wrapper: VueWrapper | undefined
let feature: ReturnType<(typeof screens)[number]['setup']>
const adapter = vi.fn<(config: InternalAxiosRequestConfig) => Promise<AxiosResponse>>()
const originalAdapter = api.defaults.adapter

function response(config: InternalAxiosRequestConfig, content: (Report | IpBlock)[], totalPages = 3) {
  return {
    config,
    status: 200,
    statusText: 'OK',
    headers: {},
    data: {
      success: true,
      data: pageResponseFixture(content, {
        number: Number(config.params.page), size: 20, totalPages, totalElements: totalPages * 20,
      }),
    },
  }
}

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore(pinia)
  auth.accessToken = 'admin-session-token'
  auth.user = { userId: 7 } as User
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  adapter.mockReset()
  api.defaults.adapter = adapter
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  client.clear()
  disposePinia(pinia)
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  api.defaults.adapter = originalAdapter
})

describe.each(screens)('$name pagination with real Vue Query and Axios', (screen) => {
  async function mountScreen() {
    adapter.mockImplementation(async config => response(config, [screen.item]))
    wrapper = mount(defineComponent({
      setup() {
        feature = screen.setup()
        return () => JSON.stringify(feature.items.value)
      },
    }), { global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }]] } })
    await vi.waitFor(() => expect(feature.totalPages.value).toBe(3))
  }

  it('keeps the requested uncached page while data is unavailable, including token rotation', async () => {
    await mountScreen()
    const pending = createDeferred<AxiosResponse>()
    let pendingConfig!: InternalAxiosRequestConfig
    adapter.mockImplementationOnce(config => {
      pendingConfig = config
      return pending.promise
    })

    feature.page.value = 1
    await vi.waitFor(() => expect(pendingConfig).toBeDefined())
    await flushPromises()

    expect(feature.items.value).toEqual([])
    expect(feature.totalPages.value).toBe(0)
    expect(feature.page.value).toBe(1)
    expect(pendingConfig.url).toBe(screen.url)
    expect(pendingConfig.params).toMatchObject({ page: 1, size: 20 })
    expect(pendingConfig.expectedSession).toEqual({ sessionGeneration: 0, userId: 7 })
    expect(pendingConfig.signal?.aborted).toBe(false)

    auth.accessToken = 'rotated-admin-token'
    await flushPromises()
    pending.resolve(response(pendingConfig, [screen.item]))
    await vi.waitFor(() => expect(feature.items.value).toEqual([screen.item]))

    expect(feature.page.value).toBe(1)
    expect(adapter.mock.calls.map(([config]) => config.params.page)).toEqual([0, 1])
  })

  it.each([0, 2])('clamps after a confirmed response shrinks the last page to %i total pages', async (totalPages) => {
    await mountScreen()
    feature.page.value = 2
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(2))
    await vi.waitFor(() => expect(feature.items.value).toHaveLength(1))
    expect(feature.page.value).toBe(2)

    adapter.mockImplementation(async config => response(config, [], totalPages))
    await client.invalidateQueries()
    await vi.waitFor(() => expect(feature.page.value).toBe(Math.max(0, totalPages - 1)))
    await vi.waitFor(() => expect(feature.totalPages.value).toBe(totalPages))
    expect(feature.items.value).toEqual([])
  })

  it('clears session details and ignores an old page response after an account switch', async () => {
    await mountScreen()
    feature.selectDetail()
    const pending = createDeferred<AxiosResponse>()
    let pendingConfig!: InternalAxiosRequestConfig
    adapter.mockImplementationOnce(config => {
      pendingConfig = config
      return pending.promise
    })
    feature.page.value = 1
    await vi.waitFor(() => expect(pendingConfig).toBeDefined())

    adapter.mockImplementation(async config => response(config, [], 3))
    auth.sessionGeneration += 1
    auth.user = { userId: 8 } as User
    auth.accessToken = 'next-admin-session-token'
    notifyAuthSessionBoundary(auth.sessionGeneration)
    clearAuthScopedQueries(client)
    await vi.waitFor(() => expect(feature.totalPages.value).toBe(3))

    expect(feature.page.value).toBe(screen.pageAfterSessionChange)
    expect(feature.isDetailModalOpen.value).toBe(false)
    expect(feature.selectedItem.value).toBeNull()
    expect(pendingConfig.signal?.aborted).toBe(true)
    pending.resolve(response(pendingConfig, [screen.item], 0))
    await flushPromises()

    expect(feature.items.value).toEqual([])
    expect(feature.totalPages.value).toBe(3)
    expect(feature.page.value).toBe(screen.pageAfterSessionChange)
    expect(adapter.mock.calls.at(-1)?.[0].expectedSession).toEqual({ sessionGeneration: 1, userId: 8 })
  })
})
