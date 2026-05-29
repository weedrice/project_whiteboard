import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { nextTick, ref } from 'vue'

const mocks = vi.hoisted(() => ({
  route: {
    params: {
      emoticonId: '7',
    },
  },
  push: vi.fn(),
  invalidateQueries: vi.fn(),
  getEmoticon: vi.fn(),
  deleteImage: vi.fn(),
  addImage: vi.fn(),
  updateEmoticon: vi.fn(),
  uploadFile: vi.fn(),
  addToast: vi.fn(),
  confirm: vi.fn(),
  toggleVisibility: vi.fn(),
  selectEmoticonImages: vi.fn(),
  createUploadableEmoticonImageFile: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.push,
  }),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn(() => ({
    data: ref({
      emoticonId: 7,
      name: 'Original',
      thumbnailUrl: 'https://cdn.example.com/thumb.png',
      tags: ['tag'],
      isActive: true,
      creatorId: 1,
      images: [{
        imageId: 10,
        emoticonId: 7,
        imageUrl: 'https://cdn.example.com/old.png',
        sortOrder: 0,
      }],
      createdAt: '2026-05-19T00:00:00',
      modifiedAt: '2026-05-19T00:00:00',
    }),
    isLoading: ref(false),
  })),
  useMutation: vi.fn(() => ({
    mutate: mocks.toggleVisibility,
    isPending: ref(false),
  })),
  useQueryClient: () => ({
    invalidateQueries: mocks.invalidateQueries,
  }),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    getEmoticon: mocks.getEmoticon,
    deleteImage: mocks.deleteImage,
    addImage: mocks.addImage,
    updateEmoticon: mocks.updateEmoticon,
    toggleVisibilityData: vi.fn(),
  },
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: mocks.uploadFile,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    user: { userId: 1 },
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/composables/useEmoticonImageSelection', () => ({
  useEmoticonImageSelection: () => ({
    selectThumbnailImage: vi.fn(),
    selectEmoticonImages: mocks.selectEmoticonImages,
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: mocks.confirm,
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

vi.mock('@/utils/imageFallback', () => ({
  applyImageFallback: vi.fn(),
}))

vi.mock('@/utils/emoticonImage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/emoticonImage')>()

  return {
    ...actual,
    createUploadableEmoticonImageFile: mocks.createUploadableEmoticonImageFile,
  }
})

import EmoticonEdit from '../EmoticonEdit.vue'

const baseButtonStub = {
  props: ['type', 'disabled'],
  template: '<button :type="type || \'button\'" :disabled="disabled"><slot /></button>',
}

function createDeferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })

  return { promise, resolve, reject }
}

describe('EmoticonEdit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
    mocks.deleteImage.mockResolvedValue({ data: { success: true } })
    mocks.addImage.mockResolvedValue({ data: { success: true } })
    mocks.updateEmoticon.mockResolvedValue({ data: { success: true } })
    mocks.createUploadableEmoticonImageFile.mockImplementation((item) => Promise.resolve(item.file))
    mocks.uploadFile.mockImplementation((file: File) => Promise.resolve({
      data: {
        data: {
          fileId: file.name === 'new-a.png' ? 101 : 102,
        }
      }
    }))
    mocks.selectEmoticonImages.mockResolvedValue([
      {
        clientId: 'new-preview-a',
        file: new File(['a'], 'new-a.png', { type: 'image/png' }),
        preview: 'blob:new-a.png',
        width: 80,
        height: 80,
      },
      {
        clientId: 'new-preview-b',
        file: new File(['b'], 'new-b.png', { type: 'image/png' }),
        preview: 'blob:new-b.png',
        width: 80,
        height: 80,
      },
    ])
  })

  it('connects edit form labels to named controls', async () => {
    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    expect(wrapper.get('label[for="emoticon-thumbnail-input"]').attributes('for')).toBe('emoticon-thumbnail-input')
    expect(wrapper.get('#emoticon-thumbnail-input').attributes()).toMatchObject({
      name: 'thumbnailImage',
      type: 'file',
    })
    expect(wrapper.get('label[for="emoticon-name-input"]').attributes('for')).toBe('emoticon-name-input')
    expect(wrapper.get('#emoticon-name-input').attributes()).toMatchObject({
      name: 'emoticonName',
      autocomplete: 'off',
    })
    expect(wrapper.get('label[for="emoticon-image-input"]').attributes('for')).toBe('emoticon-image-input')
    expect(wrapper.get('#emoticon-image-input').attributes()).toMatchObject({
      name: 'emoticonImages',
      multiple: '',
      type: 'file',
    })
    expect(wrapper.get('#emoticon-image-input').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('label[for="emoticon-tag-input"]').attributes('for')).toBe('emoticon-tag-input')
    expect(wrapper.get('#emoticon-tag-input').attributes()).toMatchObject({
      name: 'emoticonTag',
      autocomplete: 'off',
    })
  })

  it('keeps the image file input labelled when the image limit is reached', async () => {
    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    ;(wrapper.vm as unknown as { existingImages: Array<{ imageId: number; emoticonId: number; imageUrl: string; sortOrder: number }> }).existingImages = Array.from({ length: 100 }, (_, index) => ({
      imageId: index + 1,
      emoticonId: 7,
      imageUrl: `https://cdn.example.com/${index + 1}.png`,
      sortOrder: index,
    }))
    await flushPromises()

    expect(wrapper.get('label[for="emoticon-image-input"]').attributes('for')).toBe('emoticon-image-input')
    expect(wrapper.get('#emoticon-image-input').attributes('disabled')).toBeDefined()
  })

  it('deletes selected images before uploading files and adds new images in selection order', async () => {
    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    const fileInput = wrapper.find('input[type="file"][multiple]')
    Object.defineProperty(fileInput.element, 'files', {
      value: [new File(['upload'], 'selected.png', { type: 'image/png' })],
      configurable: true,
    })
    await fileInput.trigger('change')
    await flushPromises()

    await wrapper.find('button[aria-label="common.delete"]').trigger('click')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.deleteImage).toHaveBeenCalledWith(10, { skipGlobalErrorHandler: true })
    expect(mocks.uploadFile).toHaveBeenCalledTimes(2)
    expect(mocks.addImage).toHaveBeenNthCalledWith(1, 7, 101, { skipGlobalErrorHandler: true })
    expect(mocks.addImage).toHaveBeenNthCalledWith(2, 7, 102, { skipGlobalErrorHandler: true })
    expect(mocks.deleteImage.mock.invocationCallOrder[0]).toBeLessThan(
      mocks.uploadFile.mock.invocationCallOrder[0]
    )
    expect(mocks.updateEmoticon).toHaveBeenCalledWith(7, {
      name: 'Original',
      thumbnailFileId: undefined,
      tags: ['tag'],
    }, {
      skipGlobalErrorHandler: true,
    })
  })

  it('starts image add requests together and waits for all before updating metadata', async () => {
    const firstAdd = createDeferred<{ data: { success: boolean } }>()
    const secondAdd = createDeferred<{ data: { success: boolean } }>()
    mocks.addImage.mockImplementation((_: number, fileId: number) => (
      fileId === 101 ? firstAdd.promise : secondAdd.promise
    ))

    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    const fileInput = wrapper.find('input[type="file"][multiple]')
    Object.defineProperty(fileInput.element, 'files', {
      value: [new File(['upload'], 'selected.png', { type: 'image/png' })],
      configurable: true,
    })
    await fileInput.trigger('change')
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.addImage).toHaveBeenCalledTimes(2)
    expect(mocks.updateEmoticon).not.toHaveBeenCalled()

    secondAdd.resolve({ data: { success: true } })
    await flushPromises()
    expect(mocks.updateEmoticon).not.toHaveBeenCalled()

    firstAdd.resolve({ data: { success: true } })
    await flushPromises()
    expect(mocks.updateEmoticon).toHaveBeenCalledWith(7, {
      name: 'Original',
      thumbnailFileId: undefined,
      tags: ['tag'],
    }, {
      skipGlobalErrorHandler: true,
    })
  })

  it('does not delete existing images when new image preparation fails', async () => {
    mocks.createUploadableEmoticonImageFile.mockRejectedValueOnce(new Error('resize failed'))

    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    const fileInput = wrapper.find('input[type="file"][multiple]')
    Object.defineProperty(fileInput.element, 'files', {
      value: [new File(['upload'], 'selected.png', { type: 'image/png' })],
      configurable: true,
    })
    await fileInput.trigger('change')
    await flushPromises()

    ;(wrapper.vm as unknown as { imagesToDelete: number[] }).imagesToDelete = [10]
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.deleteImage).not.toHaveBeenCalled()
    expect(mocks.uploadFile).not.toHaveBeenCalled()
    expect(mocks.addImage).not.toHaveBeenCalled()
    expect(mocks.updateEmoticon).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('emoticon.edit.failed', 'error')
  })

  it('does not toggle visibility when confirm is cancelled', async () => {
    mocks.confirm.mockResolvedValue(false)

    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    const visibilityButton = wrapper.findAll('button').find((button) => button.text() === 'emoticon.visibility.hide')
    await visibilityButton?.trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledWith('emoticon.visibility.hideConfirm')
    expect(mocks.toggleVisibility).not.toHaveBeenCalled()
  })

  it('toggles visibility when confirm is accepted', async () => {
    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    const visibilityButton = wrapper.findAll('button').find((button) => button.text() === 'emoticon.visibility.hide')
    await visibilityButton?.trigger('click')
    await flushPromises()

    expect(mocks.toggleVisibility).toHaveBeenCalledTimes(1)
  })

  it('labels icon-only image and tag action buttons', async () => {
    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    expect(wrapper.get('button[aria-label="common.edit"]').attributes('title')).toBe('common.edit')
    expect(wrapper.get('button[aria-label="common.add"]').attributes('title')).toBe('common.add')
    expect(wrapper.get('button[aria-label="board.tags.remove"]').attributes('title')).toBe('board.tags.remove')

    await wrapper.get('button[aria-label="common.delete"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('button[aria-label="common.cancel"]').attributes('title')).toBe('common.cancel')
  })

  it('keeps stable tag identities when deleting duplicate tags', async () => {
    const wrapper = mount(EmoticonEdit, {
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
          EyeOff: true,
          Eye: true,
        },
      },
    })

    await flushPromises()

    const vm = wrapper.vm as unknown as {
      tags: string[]
      tagItems: Array<{ clientId: string; value: string }>
      removeTag: (clientId: string) => void
    }
    vm.tags = ['same', 'same', 'tail']
    await nextTick()

    const firstTagId = vm.tagItems[0].clientId
    const secondTagId = vm.tagItems[1].clientId

    vm.removeTag(secondTagId)
    await nextTick()

    expect(vm.tags).toEqual(['same', 'tail'])
    expect(vm.tagItems[0].clientId).toBe(firstTagId)
    expect(vm.tagItems[1].value).toBe('tail')
  })
})
