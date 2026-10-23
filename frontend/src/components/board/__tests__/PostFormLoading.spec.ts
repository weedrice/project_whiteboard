import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import PostFormHeader from '../PostFormHeader.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import {
  boardRef, mockCreateMutate, mockUpdateMutate, mockUsePostDetail, mockUseScheduledPostDetail, mockRefetchBoard,
  mountPostForm, postRef, resetPostFormTestState, scheduledPostRef,
  unmountPostFormWrappers,
} from './PostFormTestHarness'

const existingPost = (postId = 1) => ({
  postId, title: 'Original title', contents: '<p>Original body</p>',
  category: { categoryId: 1 }, tags: [], isNsfw: false, isSpoiler: false,
})

describe('PostForm resource loading', () => {
  beforeEach(resetPostFormTestState)
  afterEach(unmountPostFormWrappers)

  it('blocks every submit entry after detail failure and hydrates the original on retry', async () => {
    const refetch = vi.fn(async () => { postRef.value = existingPost() })
    mockUsePostDetail.mockReturnValue({ data: postRef, isLoading: ref(false), isError: ref(true), refetch })
    const wrapper = mountPostForm('edit')
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.getComponent(PostFormHeader).props('submitDisabled')).toBe(true)
    wrapper.getComponent(PostFormHeader).vm.$emit('submit')
    await flushPromises()
    expect(mockUpdateMutate).not.toHaveBeenCalled()

    wrapper.getComponent(ErrorState).vm.$emit('retry')
    await flushPromises()
    expect(refetch).toHaveBeenCalledOnce()
    expect((wrapper.get('#title').element as HTMLInputElement).value).toBe('Original title')
    expect((wrapper.get('[data-testid="editor-input"]').element as HTMLTextAreaElement).value).toBe('<p>Original body</p>')
  })

  it('blocks a previous post cached under a new edit identity until the matching original arrives', async () => {
    postRef.value = existingPost()
    const wrapper = mountPostForm('edit')
    await wrapper.setProps({ postId: '2' })
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.getComponent(PostFormHeader).vm.$emit('submit')
    await flushPromises()
    expect(mockUpdateMutate).not.toHaveBeenCalled()
    postRef.value = existingPost(2)
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('blocks create when required board data failed but permits callers that deliberately skip board lookup', async () => {
    boardRef.value = null
    const wrapper = mountPostForm('create')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.getComponent(PostFormHeader).vm.$emit('submit')
    await flushPromises()
    expect(mockCreateMutate).not.toHaveBeenCalled()
    await wrapper.setProps({ skipBoardLookup: true })
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('blocks scheduled editing until the matching schedule has loaded', async () => {
    const refetch = vi.fn(async () => {
      scheduledPostRef.value = {
        scheduledPostId: 44, boardUrl: 'free', title: 'Original schedule',
        contents: '<p>Scheduled body</p>', categoryId: 1, tags: [], fileIds: [],
        scheduledAt: '2099-01-01T12:00:00+09:00',
      }
    })
    mockUseScheduledPostDetail.mockReturnValue({ data: scheduledPostRef, isLoading: ref(false), refetch })
    const wrapper = mountPostForm('edit', {}, {}, { postId: '', scheduledPostId: '44' })
    expect(wrapper.find('form').exists()).toBe(false)
    scheduledPostRef.value = { scheduledPostId: 43, boardUrl: 'free', title: 'Other schedule' }
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.getComponent(ErrorState).vm.$emit('retry')
    await flushPromises()
    expect(refetch).toHaveBeenCalledOnce()
    expect((wrapper.get('#title').element as HTMLInputElement).value).toBe('Original schedule')
    expect((wrapper.get('[data-testid="editor-input"]').element as HTMLTextAreaElement).value).toBe('<p>Scheduled body</p>')
  })

  it('recovers a required board through retry before allowing composition', async () => {
    const loadedBoard = boardRef.value
    boardRef.value = null
    mockRefetchBoard.mockImplementation(async () => { boardRef.value = loadedBoard })
    const wrapper = mountPostForm('create')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.getComponent(ErrorState).vm.$emit('retry')
    await flushPromises()
    expect(mockRefetchBoard).toHaveBeenCalledOnce()
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('keeps an already hydrated editor and unsaved input during a background refetch error', async () => {
    postRef.value = existingPost()
    const error = ref(false)
    mockUsePostDetail.mockReturnValue({ data: postRef, isLoading: ref(false), isError: error, refetch: vi.fn() })
    const wrapper = mountPostForm('edit')
    await wrapper.get('#title').setValue('Unsaved changes')
    error.value = true
    await flushPromises()
    expect((wrapper.get('#title').element as HTMLInputElement).value).toBe('Unsaved changes')
    expect(wrapper.getComponent(PostFormHeader).props('submitDisabled')).toBe(false)
  })
})
