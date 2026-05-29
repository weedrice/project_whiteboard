import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EmoticonRegister from '../EmoticonRegister.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  createEmoticon: vi.fn(),
  uploadFile: vi.fn(),
  createUploadableEmoticonImageFile: vi.fn(),
  addToast: vi.fn(),
  uploadEmoticonImagePreviews: vi.fn(),
  revokeEmoticonPreviewUrl: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.push,
  }),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    createEmoticon: mocks.createEmoticon,
  },
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: mocks.uploadFile,
  },
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/composables/useEmoticonImageSelection', () => ({
  useEmoticonImageSelection: () => ({
    selectThumbnailImage: vi.fn(),
    selectEmoticonImages: vi.fn(),
  }),
}))

vi.mock('@unhead/vue', () => ({
  useHead: vi.fn(),
}))

vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/utils/emoticonImage', () => ({
  createUploadableEmoticonImageFile: mocks.createUploadableEmoticonImageFile,
  resolveEmoticonTagAddition: vi.fn(() => ({ tag: 'tag' })),
  revokeEmoticonPreviewUrl: mocks.revokeEmoticonPreviewUrl,
  SUPPORTED_EMOTICON_IMAGE_ACCEPT: 'image/png',
  uploadEmoticonImagePreviews: mocks.uploadEmoticonImagePreviews,
}))

const baseButtonStub = {
  props: ['type', 'disabled', 'loading'],
  template: '<button :type="type || \'button\'" :disabled="disabled || loading"><slot /></button>',
}

const mountRegister = () => mount(EmoticonRegister, {
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BaseButton: baseButtonStub,
      ArrowLeft: true,
      Upload: true,
      X: true,
      Plus: true,
    },
  },
})

const setValidForm = (wrapper: ReturnType<typeof mountRegister>) => {
  const vm = wrapper.vm as unknown as {
    emoticonName: string
    thumbnailFile: File
    emoticonPreviews: Array<{ clientId: string; file: File; preview: string; width: number; height: number }>
    tags: string[]
  }

  vm.emoticonName = 'Test pack'
  vm.thumbnailFile = new File(['thumb'], 'thumb.png', { type: 'image/png' })
  vm.emoticonPreviews = [{
    clientId: 'preview-image',
    file: new File(['image'], 'image.png', { type: 'image/png' }),
    preview: 'blob:image.png',
    width: 80,
    height: 80,
  }]
  vm.tags = ['fun']
}

describe('EmoticonRegister', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.uploadFile.mockImplementation((file: File) => Promise.resolve({
      data: {
        data: {
          fileId: file.name === 'thumb.png' ? 10 : 20,
        },
      },
    }))
    mocks.createEmoticon.mockResolvedValue({ data: { success: true } })
    mocks.createUploadableEmoticonImageFile.mockImplementation(async (item) => item.file)
    mocks.uploadEmoticonImagePreviews.mockImplementation(async (items, uploadFile, onProgress) => {
      const results = []
      for (const [index, item] of items.entries()) {
        results.push(await uploadFile(item.file, item, index))
        onProgress?.(index + 1, items.length)
      }
      return results
    })
  })

  it('connects register form labels to named controls', async () => {
    const wrapper = mountRegister()

    await flushPromises()

    expect(wrapper.get('label[for="emoticon-register-thumbnail-input"]').attributes('for')).toBe('emoticon-register-thumbnail-input')
    expect(wrapper.get('#emoticon-register-thumbnail-input').attributes()).toMatchObject({
      name: 'thumbnailImage',
      type: 'file',
    })
    expect(wrapper.get('label[for="emoticon-register-name-input"]').attributes('for')).toBe('emoticon-register-name-input')
    expect(wrapper.get('#emoticon-register-name-input').attributes()).toMatchObject({
      name: 'emoticonName',
      autocomplete: 'off',
    })
    expect(wrapper.get('label[for="emoticon-register-image-input"]').attributes('for')).toBe('emoticon-register-image-input')
    expect(wrapper.get('#emoticon-register-image-input').attributes()).toMatchObject({
      name: 'emoticonImages',
      multiple: '',
      type: 'file',
    })
    expect(wrapper.get('#emoticon-register-image-input').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('label[for="emoticon-register-tag-input"]').attributes('for')).toBe('emoticon-register-tag-input')
    expect(wrapper.get('#emoticon-register-tag-input').attributes()).toMatchObject({
      name: 'emoticonTag',
      autocomplete: 'off',
    })
  })

  it('keeps the image file input labelled when the image limit is reached', async () => {
    const wrapper = mountRegister()

    await flushPromises()

    ;(wrapper.vm as unknown as {
      emoticonPreviews: Array<{ clientId: string; file: File; preview: string; width: number; height: number }>
    }).emoticonPreviews = Array.from({ length: 100 }, (_, index) => ({
      clientId: `image-${index + 1}`,
      file: new File(['image'], `${index + 1}.png`, { type: 'image/png' }),
      preview: `blob:${index + 1}.png`,
      width: 80,
      height: 80,
    }))
    await flushPromises()

    expect(wrapper.get('label[for="emoticon-register-image-input"]').attributes('for')).toBe('emoticon-register-image-input')
    expect(wrapper.get('#emoticon-register-image-input').attributes('disabled')).toBeDefined()
  })

  it('labels icon-only image and tag action buttons', async () => {
    const wrapper = mountRegister()

    await flushPromises()

    const vm = wrapper.vm as unknown as {
      thumbnailPreview: string
      emoticonPreviews: Array<{ clientId: string; file: File; preview: string; width: number; height: number }>
      tags: string[]
    }
    vm.thumbnailPreview = 'blob:thumbnail.png'
    vm.emoticonPreviews = [{
      clientId: 'image-one',
      file: new File(['image'], 'one.png', { type: 'image/png' }),
      preview: 'blob:one.png',
      width: 80,
      height: 80,
    }]
    vm.tags = ['cute']
    await flushPromises()

    const deleteButtons = wrapper.findAll('button[aria-label="common.delete"]')
    expect(deleteButtons).toHaveLength(2)
    expect(deleteButtons.every((button) => button.attributes('title') === 'common.delete')).toBe(true)
    expect(wrapper.get('button[aria-label="common.add"]').attributes('title')).toBe('common.add')
    expect(wrapper.get('button[aria-label="board.tags.remove"]').attributes('title')).toBe('board.tags.remove')
  })

  it('keeps the create payload and navigates after successful uploads', async () => {
    const wrapper = mountRegister()
    setValidForm(wrapper)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.createEmoticon).toHaveBeenCalledWith({
      name: 'Test pack',
      thumbnailFileId: 10,
      tags: ['fun'],
      imageFileIds: [20],
    }, {
      skipGlobalErrorHandler: true,
    })
    expect(mocks.addToast).toHaveBeenCalledWith('emoticon.register.created', 'success')
    expect(mocks.push).toHaveBeenCalledWith({ name: 'emoticon-list' })
  })

  it('passes abort signals to thumbnail and image uploads', async () => {
    const wrapper = mountRegister()
    setValidForm(wrapper)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.uploadFile).toHaveBeenNthCalledWith(
      1,
      expect.any(File),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(mocks.uploadFile).toHaveBeenNthCalledWith(
      2,
      expect.any(File),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('aborts pending upload and suppresses side effects after unmount', async () => {
    const capturedSignal: { current: AbortSignal | null } = { current: null }
    mocks.uploadFile.mockImplementationOnce((_file: File, config?: { signal?: AbortSignal }) => {
      capturedSignal.current = config?.signal ?? null
      return new Promise(() => {})
    })

    const wrapper = mountRegister()
    setValidForm(wrapper)

    await wrapper.find('form').trigger('submit')
    expect(capturedSignal.current?.aborted).toBe(false)

    wrapper.unmount()

    expect(capturedSignal.current?.aborted).toBe(true)
    expect(mocks.createEmoticon).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(mocks.push).not.toHaveBeenCalled()
  })
})
