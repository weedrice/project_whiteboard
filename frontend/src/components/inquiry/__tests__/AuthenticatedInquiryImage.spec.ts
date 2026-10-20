import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuthenticatedInquiryImage from '../AuthenticatedInquiryImage.vue'
import { useAuthStore } from '@/stores/auth'
import { createDeferred } from '@/test/async'

const get = vi.hoisted(() => vi.fn())
vi.mock('@/api', () => ({ default: { get } }))

describe('AuthenticatedInquiryImage session ownership', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    get.mockReset()
  })
  afterEach(() => vi.restoreAllMocks())

  const attachment = { fileId: 1, url: '/api/v1/files/1', originalName: 'private.png', fileSize: 5, mimeType: 'image/png' }

  it.each([true, false])('retries once through decoding and respects focus ownership (%s)', async (ownsFocus) => {
    get.mockRejectedValueOnce(new Error('offline'))
    const pending = createDeferred<{ data: Blob }>()
    get.mockReturnValueOnce(pending.promise)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:retry')
    const wrapper = mount(AuthenticatedInquiryImage, { props: { attachment }, attachTo: document.body })
    await flushPromises()
    const button = wrapper.get('button')
    ;(button.element as HTMLButtonElement).focus()
    button.element.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    button.element.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(2)
    expect(get.mock.calls[1]![1].expectedSession).toEqual({
      sessionGeneration: useAuthStore().sessionGeneration, userId: null,
    })
    expect(wrapper.get('button').attributes('aria-disabled')).toBeDefined()
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined()
    const otherButton = document.createElement('button')
    document.body.append(otherButton)
    if (!ownsFocus) otherButton.focus()
    pending.resolve({ data: new Blob(['image']) })
    await flushPromises()
    expect(wrapper.get('button').attributes('aria-disabled')).toBeDefined()
    Object.defineProperties(wrapper.get('img').element, {
      complete: { configurable: true, value: true },
      naturalWidth: { configurable: true, value: 120 },
    })
    await wrapper.get('img').trigger('load')
    expect(wrapper.find('button').exists()).toBe(false)
    expect(document.activeElement).toBe(ownsFocus ? wrapper.get('a').element : otherButton)
    otherButton.remove()
    wrapper.unmount()
  })

  it('retries decode failures, revokes the old blob and ignores old image events', async () => {
    get.mockResolvedValue({ data: new Blob(['image']) })
    vi.spyOn(URL, 'createObjectURL').mockReturnValueOnce('blob:broken').mockReturnValueOnce('blob:fixed')
    const revoke = vi.spyOn(URL, 'revokeObjectURL')
    const wrapper = mount(AuthenticatedInquiryImage, { props: { attachment } })
    await flushPromises()
    const oldImage = wrapper.get('img')
    await oldImage.trigger('error')
    expect(wrapper.get('a').attributes('href')).toBeUndefined()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(revoke).toHaveBeenCalledWith('blob:broken')
    expect(wrapper.get('img').element).not.toBe(oldImage.element)
    await oldImage.trigger('error')
    expect(wrapper.get('img').attributes('src')).toBe('blob:fixed')
    expect(wrapper.get('img').attributes('data-image-state')).toBe('loading')
    expect(wrapper.get('button').attributes('aria-disabled')).toBeDefined()
    await wrapper.get('img').trigger('error')
    expect(wrapper.get('button').attributes('aria-disabled')).toBeUndefined()
    wrapper.unmount()
    expect(revoke).toHaveBeenCalledWith('blob:fixed')
  })

  it.each(['source', 'session', 'unmount'])('discards a delayed retry after %s changes', async (change) => {
    get.mockRejectedValueOnce(new Error('offline'))
    const pending = createDeferred<{ data: Blob }>()
    get.mockReturnValueOnce(pending.promise)
    get.mockRejectedValueOnce(new Error('still offline'))
    const create = vi.spyOn(URL, 'createObjectURL')
    const wrapper = mount(AuthenticatedInquiryImage, { props: { attachment } })
    await flushPromises()
    await wrapper.get('button').trigger('click')
    const config = get.mock.calls[1]![1]
    if (change === 'source') await wrapper.setProps({ attachment: { ...attachment, url: '/api/v1/files/2' } })
    if (change === 'session') useAuthStore().sessionGeneration += 1
    if (change === 'unmount') wrapper.unmount()
    expect(config.signal.aborted).toBe(true)
    pending.resolve({ data: new Blob(['old retry']) })
    await flushPromises()
    expect(create).not.toHaveBeenCalled()
    if (change !== 'unmount') wrapper.unmount()
  })

  it('releases visible private images and ignores delayed responses on a session change', async () => {
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:private')
    const revoke = vi.spyOn(URL, 'revokeObjectURL')
    get.mockResolvedValueOnce({ data: new Blob(['image']) })
    const wrapper = mount(AuthenticatedInquiryImage, {
      props: { attachment: { fileId: 1, url: '/api/v1/files/1', originalName: 'private.png', fileSize: 5, mimeType: 'image/png' } },
    })
    await flushPromises()
    expect(wrapper.find('img').exists()).toBe(true)
    const pending = createDeferred<{ data: Blob }>()
    get.mockReturnValueOnce(pending.promise)
    await wrapper.setProps({ attachment: { fileId: 2, url: '/api/v1/files/2', originalName: 'next.png', fileSize: 5, mimeType: 'image/png' } })
    const config = get.mock.calls[1]?.[1]
    useAuthStore().sessionGeneration += 1
    expect(config.signal.aborted).toBe(true)
    pending.resolve({ data: new Blob(['old response']) })
    await flushPromises()
    expect(wrapper.get('img').attributes('src')).toBeUndefined()
    expect(wrapper.get('img').attributes('data-image-state')).toBe('error')
    expect(revoke).toHaveBeenCalledWith('blob:private')
    expect(create).toHaveBeenCalledTimes(1)
    create.mockRestore()
    revoke.mockRestore()
    wrapper.unmount()
  })

  it('shows loading during the request, then reports a download failure', async () => {
    const pending = createDeferred<{ data: Blob }>()
    get.mockReturnValueOnce(pending.promise)
    const wrapper = mount(AuthenticatedInquiryImage, {
      props: { attachment: { fileId: 1, url: '/api/v1/files/1', originalName: 'private.png', fileSize: 5, mimeType: 'image/png' } },
    })
    expect(wrapper.get('img').attributes('data-image-state')).toBe('loading')
    pending.reject(new Error('unavailable'))
    await flushPromises()
    expect(wrapper.get('img').attributes('data-image-state')).toBe('error')
    expect(wrapper.get('a').attributes('href')).toBeUndefined()
    wrapper.unmount()
  })
})
