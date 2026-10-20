import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import { asSanitizedHtml } from '@/utils/sanitize'
import { createDeferred } from '@/test/async'
import { renderCommentContentHtml } from '@/features/comments/commentContent'
import { DEFAULT_EMOTICON_IMAGE_URL } from '@/utils/imageFallback'
import i18n, { setAppLocale } from '@/i18n'

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


  it('updates existing error labels on locale changes without replacing images or retrying downloads', async () => {
    await setAppLocale('ko')
    const pending = createDeferred<{ data: Blob }>()
    mocks.get.mockRejectedValueOnce(new Error('forbidden')).mockReturnValueOnce(pending.promise)
    const wrapper = mount(SanitizedHtmlView, {
      attachTo: document.body,
      props: { html: asSanitizedHtml(
        '<img alt="protected" src="/api/v1/files/27">'
        + '<img alt="native" src="https://cdn.noviis.kr/missing.png">'
        + '<img alt="pending" src="/api/v1/files/28">'
        + '<img alt="loaded" src="https://cdn.noviis.kr/loaded.png">',
      ) },
    })
    try {
      const images = wrapper.findAll('img')
      const elements = images.map((image) => image.element)
      elements.slice(0, 2).forEach((image) => Object.defineProperties(image, {
        clientWidth: { value: 400 }, clientHeight: { value: 200 },
      }))
      await flushPromises()
      await images[1]!.trigger('error')
      Object.defineProperties(elements[3]!, { complete: { value: true }, naturalWidth: { value: 400 } })
      await images[3]!.trigger('load')
      await flushPromises()
      const korean = i18n.global.t('common.imageLoadError')
      await vi.waitFor(() => expect(decodeURIComponent(elements[0]!.style.getPropertyValue('--nv-image-error-symbol'))).toContain(korean))
      expect(elements.map((image) => image.dataset.imageState)).toEqual(['error', 'error', 'loading', 'loaded'])
      const sources = elements.map((image) => image.getAttribute('src'))
      const stateChange = vi.fn()
      wrapper.element.addEventListener('imagestatechange', stateChange)
      await setAppLocale('en')
      await flushPromises()
      const english = i18n.global.t('common.imageLoadError')
      expect(english).not.toBe(korean)
      await vi.waitFor(() => elements.slice(0, 2).forEach((image) => {
        expect(image.title).toBe(english)
        expect(image.getAttribute('aria-label')).toBe(image.alt + ' — ' + english)
        for (const suffix of ['', '-dark']) {
          expect(decodeURIComponent(image.style.getPropertyValue('--nv-image-error-symbol' + suffix))).toContain(english)
        }
      }))
      expect(wrapper.findAll('img').map((image) => image.element)).toEqual(elements)
      expect(elements.map((image) => image.getAttribute('src'))).toEqual(sources)
      expect(elements.map((image) => image.dataset.imageState)).toEqual(['error', 'error', 'loading', 'loaded'])
      expect(elements.slice(2).map((image) => image.getAttribute('aria-label'))).toEqual([null, null])
      expect(mocks.get).toHaveBeenCalledTimes(2)
      expect(stateChange).not.toHaveBeenCalled()
      expect(createObjectUrlSpy).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
      await setAppLocale('ko')
    }
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

  it.each([true, false])('shows a terminal error for each failed content image (emoticon fallback enabled: %s)', async (useImageFallback) => {
    mocks.get.mockRejectedValue(new Error('forbidden'))
    const wrapper = mount(SanitizedHtmlView, {
      props: {
        html: asSanitizedHtml('<img src="/api/v1/files/22"><img src="/api/v1/files/22">'),
        useImageFallback,
      },
    })

    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))
    await flushPromises()
    expect(wrapper.findAll('img').map((image) => image.attributes('src'))).toEqual([undefined, undefined])
    expect(wrapper.findAll('img').map((image) => image.attributes('data-image-state'))).toEqual(['error', 'error'])
    expect(createObjectUrlSpy).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('keeps a skeleton through download until the browser decodes the blob', async () => {
    const response = createDeferred<{ data: Blob }>()
    mocks.get.mockReturnValueOnce(response.promise)
    const wrapper = mount(SanitizedHtmlView, {
      props: { html: asSanitizedHtml('<img width="640" height="480" src="/api/v1/files/25">') },
    })
    const image = wrapper.get('img')
    expect(image.attributes('data-image-state')).toBe('loading')
    await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))
    response.resolve({ data: new Blob(['image'], { type: 'image/png' }) })
    await flushPromises()
    expect(image.attributes('src')).toBe('blob:preview')
    expect(image.attributes('data-image-state')).toBe('loading')
    Object.defineProperties(image.element, {
      complete: { value: true },
      naturalWidth: { value: 640 },
    })
    await image.trigger('load')
    expect(image.attributes('data-image-state')).toBe('loaded')
    wrapper.unmount()
  })

  describe.each(['comment-emoticon', 'comment-emoticon comment-emoticon-list', 'ql-emoticon'])('emoticon fallback: %s', (className) => {
    const content = (src: string) => className === 'ql-emoticon'
      ? asSanitizedHtml(`<img class="ql-emoticon" src="${src}">`)
      : renderCommentContentHtml(`![emoticon](${src})`, className)

    it('uses the default emoticon after an authenticated download fails', async () => {
      mocks.get.mockRejectedValue(new Error('forbidden'))
      const wrapper = mount(SanitizedHtmlView, { props: { html: content('/api/v1/files/26') } })
      await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))
      await flushPromises()
      expect(wrapper.get('img').attributes('src')).toBe(DEFAULT_EMOTICON_IMAGE_URL)
      expect(wrapper.get('img').attributes('data-image-state')).toBe('loading')
    })

    it('uses the default emoticon after a native image error and terminates if the fallback fails', async () => {
      const wrapper = mount(SanitizedHtmlView, { props: { html: content('https://cdn.noviis.kr/missing.png') } })
      await flushPromises()
      const image = wrapper.get('img')
      await image.trigger('error')
      await flushPromises()
      expect(image.attributes('src')).toBe(DEFAULT_EMOTICON_IMAGE_URL)
      expect(image.attributes('data-image-state')).toBe('loading')
      await image.trigger('error')
      await flushPromises()
      expect(image.attributes('src')).toBe(DEFAULT_EMOTICON_IMAGE_URL)
      expect(image.attributes('data-image-state')).toBe('error')
    })

    it('respects callers that disable fallback for both failure paths', async () => {
      mocks.get.mockRejectedValue(new Error('forbidden'))
      const wrapper = mount(SanitizedHtmlView, {
        props: { html: content('/api/v1/files/26'), useImageFallback: false },
      })
      await vi.waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1))
      await flushPromises()
      expect(wrapper.get('img').attributes('src')).toBeUndefined()
      expect(wrapper.get('img').attributes('data-image-state')).toBe('error')
      await wrapper.setProps({ html: content('https://cdn.noviis.kr/missing.png') })
      await flushPromises()
      await wrapper.get('img').trigger('error')
      await flushPromises()
      expect(wrapper.get('img').attributes('src')).toBe('https://cdn.noviis.kr/missing.png')
      expect(wrapper.get('img').attributes('data-image-state')).toBe('error')
    })
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
