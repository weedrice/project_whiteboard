import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  addToast: vi.fn(),
  uploadFile: vi.fn(),
  createEmoticon: vi.fn(),
  selectThumbnailImage: vi.fn(),
  selectEmoticonImages: vi.fn(),
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
    selectThumbnailImage: mocks.selectThumbnailImage,
    selectEmoticonImages: mocks.selectEmoticonImages,
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

import EmoticonRegister from '../EmoticonRegister.vue'

const baseButtonStub = {
  props: ['type', 'disabled'],
  template: '<button :type="type || \'button\'" :disabled="disabled"><slot /></button>',
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

describe('EmoticonRegister', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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

    ;(wrapper.vm as unknown as { emoticonPreviews: Array<{ file: File; preview: string; width: number; height: number }> }).emoticonPreviews = Array.from({ length: 100 }, (_, index) => ({
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
      emoticonPreviews: Array<{ file: File; preview: string; width: number; height: number }>
      tags: string[]
    }
    vm.thumbnailPreview = 'blob:thumbnail.png'
    vm.emoticonPreviews = [{
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
})
