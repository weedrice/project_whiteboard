import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import api, { configureApiStoreResolvers } from '@/api'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { pageResponseFixture } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
import type { LoginHistory, PageResponse } from '@/types'
import SecuritySettingsSection from '../SecuritySettingsSection.vue'

vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: vi.fn() }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: vi.fn() }) }))
vi.mock('@/router', () => ({ default: { push: vi.fn(), currentRoute: { value: { meta: {} } } } }))
vi.mock('@/features/user/useUser', async () => {
  const { createUserFeatureContext } = await import('@/features/user/userFeatureContext')
  const { useUserSecurityFeature } = await import('@/features/user/security/useUserSecurityFeature')
  return { useUser: () => useUserSecurityFeature(createUserFeatureContext()) }
})

const auth = reactive({
  sessionGeneration: 1, user: { userId: 1 }, accessToken: 'token-a',
  fetchUser: vi.fn(async () => true), setTokens: vi.fn(),
  applyTokenIfCurrent: vi.fn(() => true), clearSessionState: vi.fn(),
})
const originalAdapter = api.defaults.adapter
let client: QueryClient
let wrapper: VueWrapper
let requests: Array<{ page: number; userId: number }>
let loadPage: (page: number, userId: number) => Promise<PageResponse<LoginHistory>>

function historyPage(page: number, userId = 1, totalPages = 2) {
  return pageResponseFixture<LoginHistory>([{
    historyId: page + 1, loginId: `user-${userId}`, ipAddress: '127.0.0.1',
    deviceSummary: `user-${userId}-page-${page}`, success: true,
    createdAt: '2026-10-27T09:00:00+09:00',
  }], { number: page, size: 10, totalPages, totalElements: totalPages === 2 ? 11 : 1 })
}

beforeEach(() => {
  auth.sessionGeneration = 1
  auth.user = { userId: 1 }
  auth.accessToken = 'token-a'
  persistAccessToken(auth.accessToken)
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user.userId)
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  requests = []
  loadPage = async (page, userId) => historyPage(page, userId)
  api.defaults.adapter = async config => {
    let data: unknown
    if (config.url === '/users/me/sessions') data = []
    else if (config.url === '/users/me/login-history') {
      const page = Number(config.params.page)
      const userId = auth.user.userId
      requests.push({ page, userId })
      data = await loadPage(page, userId)
    } else throw new Error(`Unexpected request: ${config.url}`)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data } }
  }
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
})

afterEach(() => {
  wrapper?.unmount()
  client.clear()
  api.defaults.adapter = originalAdapter
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  configureAuthQueryScope(() => 0)
  clearStoredAuthTokens(false)
})

async function openHistory() {
  wrapper = mount(SecuritySettingsSection, { global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: { RouterLink: true },
  } })
  await vi.waitFor(() => expect(wrapper.find('[aria-controls="settings-login-history"]').exists()).toBe(true))
  await wrapper.get('[aria-controls="settings-login-history"]').trigger('click')
  await vi.waitFor(() => expect(wrapper.text()).toContain('user-1-page-0'))
}

async function nextPage() {
  const buttons = wrapper.get('nav').findAll('button')
  await buttons[buttons.length - 1]!.trigger('click')
}

describe('login history pagination', () => {
  it('requests and renders older history, then returns to the first page', async () => {
    await openHistory()
    await nextPage()
    await vi.waitFor(() => expect(wrapper.text()).toContain('user-1-page-1'))
    expect(requests).toContainEqual({ page: 1, userId: 1 })
    expect(wrapper.text()).not.toContain('user-1-page-0')
    await wrapper.get('nav').findAll('button')[0]!.trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('user-1-page-0'))
  })

  it('resets to page zero on account change and ignores the old pending response', async () => {
    const pending = createDeferred<PageResponse<LoginHistory>>()
    loadPage = async (page, userId) => page === 1 && userId === 1 ? pending.promise : historyPage(page, userId)
    await openHistory()
    await nextPage()
    await vi.waitFor(() => expect(requests).toContainEqual({ page: 1, userId: 1 }))
    auth.user = { userId: 2 }
    auth.sessionGeneration = 2
    auth.accessToken = 'token-b'
    persistAccessToken(auth.accessToken)
    notifyAuthSessionBoundary(2)
    await flushPromises()
    expect(wrapper.find('#settings-login-history').exists()).toBe(false)
    await wrapper.get('[aria-controls="settings-login-history"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('user-2-page-0'))
    pending.resolve(historyPage(1))
    await flushPromises()
    expect(wrapper.text()).not.toContain('user-1-page-1')
    expect(requests).toContainEqual({ page: 0, userId: 2 })
  })

  it('returns to the last available page when retention removes the requested page', async () => {
    loadPage = async page => page === 1
      ? pageResponseFixture<LoginHistory>([], { number: 1, size: 10, totalPages: 1, totalElements: 1 })
      : historyPage(0)
    await openHistory()
    await nextPage()
    await vi.waitFor(() => expect(requests).toContainEqual({ page: 1, userId: 1 }))
    await vi.waitFor(() => expect(wrapper.text()).toContain('user-1-page-0'))
    expect(wrapper.get('[aria-current="page"]').text()).toBe('1')
  })
})
