import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getAd: vi.fn(),
  recordImpression: vi.fn(),
  recordClick: vi.fn(),
  t: vi.fn((key: string) => key),
  loggerWarn: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/api/ad', () => ({
  adApi: {
    getAd: mocks.getAd,
    recordImpression: mocks.recordImpression,
    recordClick: mocks.recordClick,
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
    mocks.getAd.mockResolvedValue({
      adId: 1,
      title: 'Top banner',
      imageUrl: 'https://cdn.test/banner.png',
      targetUrl: 'https://example.com',
    })
    mocks.recordImpression.mockResolvedValue(undefined)
    mocks.recordClick.mockResolvedValue('https://example.com')
  })

  it('records an impression after fetch and preserves click flow', async () => {
    const openMock = vi.spyOn(window, 'open').mockReturnValue({ opener: window } as unknown as Window)
    const wrapper = mount(AdBanner, {
      props: { placement: 'TOP' },
    })

    await flushPromises()

    expect(mocks.getAd).toHaveBeenCalledWith('TOP', expect.objectContaining({
      signal: expect.any(AbortSignal),
    }))
    expect(mocks.recordImpression).toHaveBeenCalledWith(1, expect.objectContaining({
      signal: expect.any(AbortSignal),
    }))

    await wrapper.get('.cursor-pointer').trigger('click')
    await flushPromises()

    expect(mocks.recordClick).toHaveBeenCalledWith(1)
    expect(openMock).toHaveBeenCalledWith('https://example.com/', '_blank', 'noopener,noreferrer')

    openMock.mockRestore()
  })

  it('falls back to the fetched targetUrl when click recording fails', async () => {
    mocks.recordClick.mockRejectedValue(new Error('404'))

    const openMock = vi.spyOn(window, 'open').mockReturnValue({ opener: window } as unknown as Window)
    const wrapper = mount(AdBanner, {
      props: { placement: 'TOP' },
    })

    await flushPromises()
    await wrapper.get('.cursor-pointer').trigger('click')
    await flushPromises()

    expect(mocks.recordClick).toHaveBeenCalledWith(1)
    expect(openMock).toHaveBeenCalledWith('https://example.com/', '_blank', 'noopener,noreferrer')

    openMock.mockRestore()
  })
})
