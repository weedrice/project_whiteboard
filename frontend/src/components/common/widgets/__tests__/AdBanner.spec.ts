import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  t: vi.fn((key: string) => key),
  loggerWarn: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/api', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
  },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: mocks.t,
  }),
}))

vi.mock('@/utils/logger', () => ({
  default: {
    warn: mocks.loggerWarn,
    error: mocks.loggerError,
  },
}))

import AdBanner from '../AdBanner.vue'

const flushPromises = async () => {
  await Promise.resolve()
  await Promise.resolve()
}

describe('AdBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({
      data: {
        success: true,
        data: {
          adId: 1,
          title: 'Top banner',
          imageUrl: 'https://cdn.test/banner.png',
          targetUrl: 'https://example.com',
        },
      },
    })
    mocks.post.mockImplementation((url: string) => {
      if (url.endsWith('/impression')) {
        return Promise.resolve({ data: { success: true } })
      }
      if (url.endsWith('/click')) {
        return Promise.resolve({ data: { success: true, data: 'https://example.com' } })
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })
  })

  it('records an impression after fetch and preserves click flow', async () => {
    const openMock = vi.spyOn(window, 'open').mockReturnValue({ opener: window } as unknown as Window)
    const wrapper = mount(AdBanner, {
      props: { placement: 'TOP' },
    })

    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/ads', {
      params: { placement: 'TOP' },
    })
    expect(mocks.post).toHaveBeenCalledWith('/ads/1/impression')

    await wrapper.get('.cursor-pointer').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/ads/1/click')
    expect(openMock).toHaveBeenCalledWith('https://example.com/', '_blank', 'noopener,noreferrer')

    openMock.mockRestore()
  })

  it('falls back to the fetched targetUrl when click recording fails', async () => {
    mocks.post.mockImplementation((url: string) => {
      if (url.endsWith('/impression')) {
        return Promise.resolve({ data: { success: true } })
      }
      if (url.endsWith('/click')) {
        return Promise.reject(new Error('404'))
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })

    const openMock = vi.spyOn(window, 'open').mockReturnValue({ opener: window } as unknown as Window)
    const wrapper = mount(AdBanner, {
      props: { placement: 'TOP' },
    })

    await flushPromises()
    await wrapper.get('.cursor-pointer').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/ads/1/click')
    expect(openMock).toHaveBeenCalledWith('https://example.com/', '_blank', 'noopener,noreferrer')

    openMock.mockRestore()
  })
})
