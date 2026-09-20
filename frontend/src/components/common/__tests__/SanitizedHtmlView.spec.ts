import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import { asSanitizedHtml } from '@/utils/sanitize'
import { createDeferred } from '@/test/async'

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
      props: { html: asSanitizedHtml('<img src="/api/v1/files/21"><img src="/api/v1/files/21">') },
    })
    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))

    notifyAuthSessionBoundary(2)
    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(2))

    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:preview')

    wrapper.unmount()
    expect(revokeObjectUrlSpy).toHaveBeenCalledTimes(2)
  })

  it('shares one request and object URL for repeated normalized image paths', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['image'], { type: 'image/png' }) })
    const wrapper = mount(SanitizedHtmlView, {
      props: {
        html: asSanitizedHtml(
          `<img src="/api/v1/files/17?size=sm"><img src="${window.location.origin}/api/v1/files/17?size=sm">`,
        ),
      },
    })

    await vi.waitFor(() => expect(createObjectUrlSpy).toHaveBeenCalledTimes(1))
    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(mocks.get).toHaveBeenCalledWith('/files/17?size=sm', expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(wrapper.findAll('img').map((image) => image.attributes('src'))).toEqual(['blob:preview', 'blob:preview'])

    wrapper.unmount()
    expect(revokeObjectUrlSpy).toHaveBeenCalledExactlyOnceWith('blob:preview')
  })

  it('keeps different file variants and query strings in separate requests', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['image'], { type: 'image/png' }) })
    createObjectUrlSpy
      .mockReturnValueOnce('blob:small')
      .mockReturnValueOnce('blob:large')
      .mockReturnValueOnce('blob:variant')
    const wrapper = mount(SanitizedHtmlView, {
      props: {
        html: asSanitizedHtml(
          '<img src="/api/v1/files/17?size=sm"><img src="/api/v1/files/17?size=lg">'
          + '<img src="/api/v1/files/17/variants/thumbnail"><img src="/api/v1/files/17?size=sm">',
        ),
      },
    })

    await vi.waitFor(() => expect(createObjectUrlSpy).toHaveBeenCalledTimes(3))
    expect(mocks.get.mock.calls.map(([path]) => path)).toEqual([
      '/files/17?size=sm', '/files/17?size=lg', '/files/17/variants/thumbnail',
    ])
    expect(wrapper.findAll('img').map((image) => image.attributes('src'))).toEqual([
      'blob:small', 'blob:large', 'blob:variant', 'blob:small',
    ])
    wrapper.unmount()
    expect(revokeObjectUrlSpy).toHaveBeenCalledTimes(3)
  })

  it.each([true, false])('preserves fallback policy %s for every image sharing a failed request', async (useImageFallback) => {
    mocks.get.mockRejectedValue(new Error('forbidden'))
    const wrapper = mount(SanitizedHtmlView, {
      props: {
        html: asSanitizedHtml('<img src="/api/v1/files/22"><img src="/api/v1/files/22">'),
        useImageFallback,
      },
    })

    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))
    await flushPromises()
    const expectedSource = useImageFallback ? '/images/default-emoticon.png' : undefined
    expect(wrapper.findAll('img').map((image) => image.attributes('src'))).toEqual([expectedSource, expectedSource])
    expect(createObjectUrlSpy).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it.each(['content', 'session'])('ignores a shared request resolved after its %s generation changed', async (boundary) => {
    const previous = createDeferred<{ data: Blob }>()
    mocks.get
      .mockReturnValueOnce(previous.promise)
      .mockResolvedValueOnce({ data: new Blob(['current'], { type: 'image/png' }) })
    const wrapper = mount(SanitizedHtmlView, {
      props: { html: asSanitizedHtml('<img src="/api/v1/files/23"><img src="/api/v1/files/23">') },
    })
    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))
    const previousSignal = mocks.get.mock.calls[0]?.[1].signal as AbortSignal

    if (boundary === 'session') notifyAuthSessionBoundary(2)
    else await wrapper.setProps({
      html: asSanitizedHtml('<img alt="updated" src="/api/v1/files/23"><img src="/api/v1/files/23">'),
    })
    await vi.waitFor(() => expect(createObjectUrlSpy).toHaveBeenCalledTimes(1))
    expect(mocks.get).toHaveBeenCalledTimes(2)
    expect(previousSignal.aborted).toBe(true)

    previous.resolve({ data: new Blob(['stale'], { type: 'image/png' }) })
    await flushPromises()
    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('img').map((image) => image.attributes('src'))).toEqual(['blob:preview', 'blob:preview'])
    wrapper.unmount()
    expect(revokeObjectUrlSpy).toHaveBeenCalledExactlyOnceWith('blob:preview')
  })

  it('aborts an unfinished shared request on unmount without creating an object URL', async () => {
    const response = createDeferred<{ data: Blob }>()
    mocks.get.mockReturnValueOnce(response.promise)
    const wrapper = mount(SanitizedHtmlView, {
      props: { html: asSanitizedHtml('<img src="/api/v1/files/24"><img src="/api/v1/files/24">') },
    })
    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))
    const signal = mocks.get.mock.calls[0]?.[1].signal as AbortSignal

    wrapper.unmount()
    expect(signal.aborted).toBe(true)
    response.resolve({ data: new Blob(['late'], { type: 'image/png' }) })
    await flushPromises()
    expect(createObjectUrlSpy).not.toHaveBeenCalled()
    expect(revokeObjectUrlSpy).not.toHaveBeenCalled()
  })
})
