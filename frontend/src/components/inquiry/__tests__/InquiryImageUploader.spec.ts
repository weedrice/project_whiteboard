import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InquiryImageUploader from '../InquiryImageUploader.vue'

const { uploadFile, discardUploads, discardUploadsOnPageExit } = vi.hoisted(() => ({
  uploadFile: vi.fn(),
  discardUploads: vi.fn().mockResolvedValue(undefined),
  discardUploadsOnPageExit: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile,
    discardUploads,
    discardUploadsOnPageExit,
  },
}))

describe('InquiryImageUploader', () => {
  beforeEach(() => {
    uploadFile.mockReset()
    discardUploads.mockClear()
    discardUploadsOnPageExit.mockClear()
  })

  it('disables file selection and removal while its parent mutation is pending', async () => {
    const wrapper = mount(InquiryImageUploader, {
      props: { disabled: true, modelValue: [101] },
    })

    expect(wrapper.get('input[type="file"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()

    await wrapper.get('button').trigger('click')

    expect(discardUploads).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('discards files uploaded earlier in the same batch when a later upload fails', async () => {
    uploadFile
      .mockResolvedValueOnce({ data: { success: true, data: { fileId: 101 } } })
      .mockRejectedValueOnce(new Error('upload failed'))
    const wrapper = mount(InquiryImageUploader)
    const input = wrapper.get('input[type="file"]')
    const first = new File(['first'], 'first.png', { type: 'image/png' })
    const second = new File(['second'], 'second.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [first, second] })

    await input.trigger('change')
    await flushPromises()

    expect(discardUploads).toHaveBeenCalledWith([101], { skipGlobalErrorHandler: true })
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual([])
    expect(wrapper.emitted('error')).toHaveLength(1)
  })

  it('announces an in-flight upload and aborts its request when unmounted', async () => {
    let capturedSignal: AbortSignal | undefined
    uploadFile.mockImplementation((_file, options) => {
      capturedSignal = options.signal
      return new Promise(() => {})
    })
    const wrapper = mount(InquiryImageUploader)
    const input = wrapper.get('input[type="file"]')
    const file = new File(['image'], 'image.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })

    void input.trigger('change')
    await Promise.resolve()

    expect(wrapper.emitted('uploading')?.[0]).toEqual([true])
    expect(capturedSignal?.aborted).toBe(false)

    wrapper.unmount()

    expect(capturedSignal?.aborted).toBe(true)
    expect(discardUploadsOnPageExit).not.toHaveBeenCalled()
  })

  it('does not let an aborted in-flight upload mutate the next draft', async () => {
    let resolveUpload!: (value: unknown) => void
    uploadFile.mockImplementation(() => new Promise((resolve) => {
      resolveUpload = resolve
    }))
    const updateModel = vi.fn()
    const wrapper = mount(InquiryImageUploader, {
      props: {
        modelValue: [],
        'onUpdate:modelValue': updateModel,
      },
    })
    const input = wrapper.get('input[type="file"]')
    const file = new File(['image'], 'delayed.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })

    void input.trigger('change')
    await Promise.resolve()
    const uploader = wrapper.vm as unknown as { discardUploads: () => Promise<void> }
    const discardPromise = uploader.discardUploads()
    await wrapper.setProps({ modelValue: [909] })
    resolveUpload({ data: { success: true, data: { fileId: 404 } } })
    await discardPromise
    await flushPromises()

    expect(discardUploads).toHaveBeenCalledWith([404], { skipGlobalErrorHandler: true })
    expect(updateModel).not.toHaveBeenCalled()
    expect(wrapper.emitted('uploading')).toEqual([[true], [false]])
    expect(wrapper.get('input[type="file"]').attributes('disabled')).toBeUndefined()
  })

  it('preserves submitted uploads on unmount and only discards them after submission failure', async () => {
    uploadFile.mockResolvedValueOnce({ data: { success: true, data: { fileId: 202 } } })
    const wrapper = mount(InquiryImageUploader)
    const input = wrapper.get('input[type="file"]')
    const file = new File(['image'], 'submitted.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })

    await input.trigger('change')
    await flushPromises()
    const uploader = wrapper.vm as unknown as {
      beginSubmission: () => boolean
      failSubmission: () => Promise<void>
    }

    expect(uploader.beginSubmission()).toBe(true)
    wrapper.unmount()

    expect(discardUploadsOnPageExit).not.toHaveBeenCalled()

    await uploader.failSubmission()

    expect(discardUploads).toHaveBeenCalledWith([202], { skipGlobalErrorHandler: true })
  })

  it('locks its own controls immediately and does not mutate the parent model when submission completes', async () => {
    uploadFile.mockResolvedValueOnce({ data: { success: true, data: { fileId: 303 } } })
    const wrapper = mount(InquiryImageUploader)
    const input = wrapper.get('input[type="file"]')
    const file = new File(['image'], 'locked.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })

    await input.trigger('change')
    await flushPromises()
    const updateCountBeforeSubmission = wrapper.emitted('update:modelValue')?.length ?? 0
    const uploader = wrapper.vm as unknown as {
      beginSubmission: () => boolean
      commitUploads: () => void
    }

    expect(uploader.beginSubmission()).toBe(true)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('input[type="file"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()

    await wrapper.get('button').trigger('click')
    uploader.commitUploads()
    await wrapper.vm.$nextTick()

    expect(discardUploads).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toHaveLength(updateCountBeforeSubmission)
  })
})
