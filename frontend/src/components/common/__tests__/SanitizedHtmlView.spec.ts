import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import { asSanitizedHtml } from '@/utils/sanitize'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/api', () => ({
  default: { get: mocks.get },
}))

describe('SanitizedHtmlView', () => {
  let createObjectUrlSpy: ReturnType<typeof vi.spyOn>
  let revokeObjectUrlSpy: ReturnType<typeof vi.spyOn>
  let anchorClickSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    mocks.get.mockReset()
    createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview')
    revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    anchorClickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  afterEach(() => {
    createObjectUrlSpy.mockRestore()
    revokeObjectUrlSpy.mockRestore()
    anchorClickSpy.mockRestore()
  })

  it('loads local protected images through the authenticated API client', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['image'], { type: 'image/png' }) })
    const wrapper = mount(SanitizedHtmlView, {
      props: {
        html: asSanitizedHtml(
          '<img alt="protected" src="/api/v1/files/17?size=sm"><img alt="cdn" src="https://cdn.noviis.kr/files/18">',
        ),
      },
    })

    const protectedImage = wrapper.get('img[alt="protected"]')
    expect(protectedImage.attributes('src')).toBeUndefined()
    expect(wrapper.get('img[alt="cdn"]').attributes('src')).toBe('https://cdn.noviis.kr/files/18')

    await vi.waitFor(() => {
      expect(mocks.get).toHaveBeenCalledWith('/files/17?size=sm', expect.objectContaining({
        responseType: 'blob',
        skipGlobalErrorHandler: true,
      }))
    })
    await flushPromises()
    expect(protectedImage.attributes('src')).toBe('blob:preview')
    wrapper.unmount()
  })

  it('loads local protected attachment links only when activated', async () => {
    mocks.get.mockResolvedValue({
      data: new Blob(['file'], { type: 'application/pdf' }),
      headers: {
        'content-disposition': "attachment; filename=report.pdf; filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C.pdf",
      },
    })
    const wrapper = mount(SanitizedHtmlView, {
      props: {
        html: asSanitizedHtml(
          '<a id="protected" href="/api/v1/files/31?download=true">download</a><a id="external" href="https://cdn.noviis.kr/files/31">external</a>',
        ),
      },
    })

    expect(wrapper.get('#protected').attributes('href')).toBeUndefined()
    expect(wrapper.get('#protected').attributes('role')).toBe('link')
    expect(wrapper.get('#protected').attributes('tabindex')).toBe('0')
    expect(wrapper.get('#external').attributes('href')).toBe('https://cdn.noviis.kr/files/31')
    await flushPromises()
    expect(mocks.get).not.toHaveBeenCalled()

    await wrapper.get('#protected').trigger('click')

    await vi.waitFor(() => {
      expect(mocks.get).toHaveBeenCalledWith('/files/31?download=true', expect.objectContaining({
        responseType: 'blob',
        skipGlobalErrorHandler: true,
      }))
      expect(anchorClickSpy).toHaveBeenCalledTimes(1)
    })
    expect(wrapper.get('#protected').attributes('href')).toBeUndefined()
    const clickedAnchor = anchorClickSpy.mock.instances[0] as HTMLAnchorElement
    expect(clickedAnchor.href).toBe('blob:preview')
    expect(clickedAnchor.download).toBe('보고서.pdf')
    wrapper.unmount()
  })

  it('disables a protected attachment link when its authenticated request fails', async () => {
    mocks.get.mockRejectedValue(new Error('forbidden'))
    const wrapper = mount(SanitizedHtmlView, {
      props: { html: asSanitizedHtml('<a id="protected" href="/api/v1/files/32">download</a>') },
    })

    await flushPromises()
    expect(mocks.get).not.toHaveBeenCalled()
    await wrapper.get('#protected').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.get('#protected').attributes('aria-disabled')).toBe('true')
    })
    expect(wrapper.get('#protected').attributes('href')).toBeUndefined()
    wrapper.unmount()
  })

  it('revokes old object URLs and reloads images at an authentication boundary', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['image'], { type: 'image/png' }) })
    const wrapper = mount(SanitizedHtmlView, {
      props: { html: asSanitizedHtml('<img src="/api/v1/files/21">') },
    })
    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))

    notifyAuthSessionBoundary(2)
    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(2))

    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:preview')

    wrapper.unmount()
    expect(revokeObjectUrlSpy).toHaveBeenCalledTimes(2)
  })

  it('uses the existing fallback when an authenticated image request fails', async () => {
    mocks.get.mockRejectedValue(new Error('forbidden'))
    const wrapper = mount(SanitizedHtmlView, {
      props: { html: asSanitizedHtml('<img alt="protected" src="/api/v1/files/22">') },
    })

    await vi.waitFor(() => {
      expect(wrapper.get('img').attributes('src')).toBe('/images/default-emoticon.png')
    })
    wrapper.unmount()
  })
})
