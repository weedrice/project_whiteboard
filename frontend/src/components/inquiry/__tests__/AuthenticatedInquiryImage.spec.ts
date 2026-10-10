import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
    expect(wrapper.find('img').exists()).toBe(false)
    expect(revoke).toHaveBeenCalledWith('blob:private')
    expect(create).toHaveBeenCalledTimes(1)
    create.mockRestore()
    revoke.mockRestore()
  })
})
