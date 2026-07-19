import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EmoticonRegister from '../EmoticonRegister.vue'
import { emoticonApiData, emoticonApiSuccess } from '@/test/emoticonApiFixtures'
import { getExposedVm } from '@/test/vue-test-helpers'
import { ref } from 'vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

type EmoticonPreviewFixture = { clientId: string; file: File; preview: string; width: number; height: number }
type EmoticonRegisterExposed = {
  emoticonName: string
  isSubmitting: boolean
  thumbnailFile: File | null
  thumbnailPreview: string | null
  emoticonPreviews: EmoticonPreviewFixture[]
  tagInput: string
  tags: string[]
  uploadProgress: { current: number; total: number }
}

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  createEmoticon: vi.fn(),
  uploadFile: vi.fn(),
  createUploadableEmoticonImageFile: vi.fn(),
  createUploadableEmoticonThumbnailFile: vi.fn(),
  addToast: vi.fn(),
  uploadEmoticonImagePreviews: vi.fn(),
  revokeEmoticonPreviewUrl: vi.fn(),
  refreshImagePolicy: vi.fn(),
  routeLeaveGuard: null as null | (() => boolean),
}))

vi.mock('@/features/emoticon/form/useEmoticonImagePolicy', () => ({
  useEmoticonImagePolicy: () => ({
    maxImageCount: ref(20),
    refresh: mocks.refreshImagePolicy,
  }),
}))

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: () => boolean) => { mocks.routeLeaveGuard = guard },
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

vi.mock('@/features/emoticon/form/useEmoticonImageSelection', () => ({
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
  extractErrorCode: vi.fn(() => null),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/utils/emoticonImage', () => ({
  createUploadableEmoticonImageFile: mocks.createUploadableEmoticonImageFile,
  createUploadableEmoticonThumbnailFile: mocks.createUploadableEmoticonThumbnailFile,
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
  const vm = getExposedVm<EmoticonRegisterExposed>(wrapper)

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
    mocks.routeLeaveGuard = null
    mocks.uploadFile.mockImplementation((file: File) => Promise.resolve(
      emoticonApiData({ fileId: file.name === 'thumb.png' ? 10 : 20 })
    ))
    mocks.createEmoticon.mockResolvedValue(emoticonApiSuccess())
    mocks.createUploadableEmoticonImageFile.mockImplementation(async (item) => item.file)
    mocks.createUploadableEmoticonThumbnailFile.mockImplementation(async (file) => file)
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

    getExposedVm<Pick<EmoticonRegisterExposed, 'emoticonPreviews'>>(wrapper).emoticonPreviews = Array.from({ length: 20 }, (_, index) => ({
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

    const vm = getExposedVm<Pick<EmoticonRegisterExposed, 'thumbnailPreview' | 'emoticonPreviews' | 'tags'>>(wrapper)
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
    expect(mocks.routeLeaveGuard?.()).toBe(false)

    wrapper.unmount()

    expect(capturedSignal.current?.aborted).toBe(true)
    expect(mocks.createEmoticon).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(mocks.push).not.toHaveBeenCalled()
  })

  it('cancels uploads and clears account-owned draft state at the auth boundary', async () => {
    let rejectUpload: ((reason?: unknown) => void) | undefined
    const capturedSignal: { current: AbortSignal | null } = { current: null }
    mocks.uploadFile.mockImplementationOnce((_file: File, config?: { signal?: AbortSignal }) => {
      capturedSignal.current = config?.signal ?? null
      return new Promise((_resolve, reject) => {
        rejectUpload = reject
      })
    })

    const wrapper = mountRegister()
    setValidForm(wrapper)
    const vm = getExposedVm<EmoticonRegisterExposed>(wrapper)
    vm.thumbnailPreview = 'blob:thumbnail.png'
    vm.tagInput = 'draft-tag'

    await wrapper.find('form').trigger('submit')
    expect(vm.isSubmitting).toBe(true)
    expect(capturedSignal.current?.aborted).toBe(false)

    notifyAuthSessionBoundary(1)
    await flushPromises()

    expect(capturedSignal.current?.aborted).toBe(true)
    expect(vm.isSubmitting).toBe(false)
    expect(vm.emoticonName).toBe('')
    expect(vm.thumbnailFile).toBeNull()
    expect(vm.thumbnailPreview).toBeNull()
    expect(vm.emoticonPreviews).toEqual([])
    expect(vm.tagInput).toBe('')
    expect(vm.tags).toEqual([])
    expect(vm.uploadProgress).toEqual({ current: 0, total: 0 })
    expect(mocks.revokeEmoticonPreviewUrl).toHaveBeenCalledWith('blob:thumbnail.png')
    expect(mocks.revokeEmoticonPreviewUrl).toHaveBeenCalledWith('blob:image.png')

    rejectUpload?.(new DOMException('cancelled', 'AbortError'))
    await flushPromises()

    expect(mocks.createEmoticon).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(mocks.push).not.toHaveBeenCalled()
  })
})
