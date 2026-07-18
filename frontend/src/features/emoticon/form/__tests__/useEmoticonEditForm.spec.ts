import { describe, expect, it, vi, beforeEach } from 'vitest'
import { defineComponent, computed, nextTick, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useEmoticonEditForm } from '../useEmoticonEditForm'
import { toEmoticonEditFormState } from '../useEmoticonEditResource'

const mocks = vi.hoisted(() => ({
  toggleVisibility: vi.fn(),
}))

vi.mock('@/features/emoticon/useToggleEmoticonVisibility', () => ({
  useToggleEmoticonVisibility: () => ({
    mutate: mocks.toggleVisibility,
    isPending: ref(false),
  }),
}))

const createEmoticon = () => ref({
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
})

const createConfirmMock = () => vi.fn<(message: string) => Promise<boolean>>().mockResolvedValue(true)

const mountForm = (options: {
  confirm?: ReturnType<typeof createConfirmMock>
  emoticon?: ReturnType<typeof createEmoticon>
  emoticonId?: Ref<number>
} = {}) => {
  const confirm = options.confirm ?? createConfirmMock()
  const emoticonId = options.emoticonId ?? ref(7)
  let form!: ReturnType<typeof useEmoticonEditForm>
  const wrapper = mount(defineComponent({
    setup() {
      const emoticon = options.emoticon ?? createEmoticon()
      form = useEmoticonEditForm({
        emoticonId: computed(() => emoticonId.value),
        emoticon,
        editFormState: computed(() => toEmoticonEditFormState(emoticon.value)),
        selectThumbnailImage: vi.fn(),
        selectEmoticonImages: vi.fn(),
        confirm,
        t: (key: string) => key,
        onMaxTags: vi.fn(),
        maxImageCount: computed(() => 20),
      })

      return () => null
    },
  }))

  return { confirm, emoticonId, form, wrapper }
}

describe('useEmoticonEditForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('hydrates editable fields from the loaded emoticon once', () => {
    const { form, wrapper } = mountForm()

    expect(form.emoticonName.value).toBe('Original')
    expect(form.tags.value).toEqual(['tag'])
    expect(form.existingImages.value).toHaveLength(1)
    expect(form.thumbnailPreview.value).toBe('https://cdn.example.com/thumb.png')
    expect(form.totalImageCount.value).toBe(1)
    expect(form.isFormValid.value).toBe(true)
    wrapper.unmount()
  })

  it('tracks existing image deletion state and form validity', () => {
    const { form, wrapper } = mountForm()

    form.markImageForDeletion(10)

    expect(form.imagesToDelete.value).toEqual([10])
    expect(form.totalImageCount.value).toBe(0)
    expect(form.isFormValid.value).toBe(false)

    form.unmarkImageForDeletion(10)

    expect(form.imagesToDelete.value).toEqual([])
    expect(form.totalImageCount.value).toBe(1)
    expect(form.isFormValid.value).toBe(true)
    wrapper.unmount()
  })

  it('toggles visibility only after confirmation', async () => {
    const confirm = createConfirmMock().mockResolvedValueOnce(false).mockResolvedValueOnce(true)
    const { form, wrapper } = mountForm({ confirm })

    await form.handleToggleVisibility()
    await form.handleToggleVisibility()

    expect(confirm).toHaveBeenCalledWith('emoticon.visibility.hideConfirm')
    expect(mocks.toggleVisibility).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('cancels the active submit and clears route-local form state when the emoticon changes', async () => {
    const { emoticonId, form, wrapper } = mountForm()
    const cancelSubmitRun = vi.spyOn(form.uploadSession, 'cancelSubmitRun')
    const activeRunId = form.uploadSession.startSubmitRun()
    const file = new File(['new'], 'new.png', { type: 'image/png' })
    form.isSubmitting.value = true
    form.emoticonName.value = 'Changed'
    form.imagesToDelete.value = [10]
    form.thumbnailFile.value = file
    form.thumbnailPreview.value = 'blob:thumbnail'
    form.newEmoticonPreviews.value = [{
      clientId: 'new',
      file,
      preview: 'blob:new',
      width: 100,
      height: 100,
    }]
    form.tagInput.value = 'pending'
    form.tags.value = ['changed']
    form.uploadSession.setUploadProgress(1, 2)

    emoticonId.value = 8
    await nextTick()

    expect(cancelSubmitRun).toHaveBeenCalledTimes(1)
    expect(form.uploadSession.isSubmitActive(activeRunId)).toBe(false)
    expect(form.isSubmitting.value).toBe(false)
    expect(form.emoticonName.value).toBe('')
    expect(form.existingImages.value).toEqual([])
    expect(form.imagesToDelete.value).toEqual([])
    expect(form.thumbnailFile.value).toBeNull()
    expect(form.thumbnailPreview.value).toBeNull()
    expect(form.newEmoticonPreviews.value).toEqual([])
    expect(form.tagInput.value).toBe('')
    expect(form.tags.value).toEqual([])
    expect(form.uploadProgress.value).toEqual({ current: 0, total: 0 })
    wrapper.unmount()
  })
})
