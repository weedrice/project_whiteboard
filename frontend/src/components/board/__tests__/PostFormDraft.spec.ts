import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import {
  findButtonByText,
  getLastCreatePostVariables,
  getLastUpdatePostVariables,
  mockAddToast,
  mockPostFormAuthStore,
  mockSaveDraftMutateAsync,
  mountPostForm,
  postRef,
  resetPostFormTestState,
  routeState,
  setBoardCategories,
  unmountPostFormWrappers,
} from './PostFormTestHarness'

describe('PostForm draft behavior', () => {
  afterEach(() => {
    unmountPostFormWrappers()
  })

  beforeEach(() => {
    resetPostFormTestState()
  })

  it('saves a draft from the header action when drafts are enabled', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const wrapper = mountPostForm('create')

    await wrapper.get('#title').setValue('Draft title')
    await wrapper.get('[data-testid="editor-input"]').setValue('Draft body')
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockSaveDraftMutateAsync).toHaveBeenCalled()
    expect(mockAddToast).toHaveBeenCalledWith('board.writePost.draftStatus.saved', 'success')
  })

  it('shows an error toast when manual draft save fails', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    mockSaveDraftMutateAsync.mockRejectedValueOnce(new Error('save failed'))
    const wrapper = mountPostForm('create')

    await wrapper.get('#title').setValue('Draft title')
    await wrapper.get('[data-testid="editor-input"]').setValue('Draft body')
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockAddToast).toHaveBeenCalledWith('common.messages.saveFailed', 'error')
  })

  it('saves a body-only draft to the server without requiring a title', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()

    await wrapper.get('[data-testid="editor-input"]').setValue('Body without a title')
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockSaveDraftMutateAsync).toHaveBeenCalledWith(expect.objectContaining({
      title: '',
      contents: 'Body without a title',
    }))
    expect(mockAddToast).toHaveBeenCalledWith('board.writePost.draftStatus.saved', 'success')
  })

  it('saves the draft before create submit and includes draft id', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    setBoardCategories([{ categoryId: 12, name: 'General', minWriteRole: 'USER' }])
    const wrapper = mountPostForm('create')

    await wrapper.get('#title').setValue('Created title')
    await wrapper.get('#category').setValue('12')
    await wrapper.get('[data-testid="editor-input"]').setValue('Created body')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const variables = getLastCreatePostVariables()
    expect(variables.data.draftId).toBe(91)
  })

  it('saves the draft before update submit and includes draft id', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    routeState.params.postId = '77'
    postRef.value = {
      postId: 77,
      title: 'Before title',
      contents: 'Before body',
      category: { categoryId: 5 },
      tags: ['before'],
      isNsfw: false,
      isSpoiler: false,
    }
    const wrapper = mountPostForm('edit')
    await nextTick()

    await wrapper.get('#title').setValue('After title')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const variables = getLastUpdatePostVariables()
    expect(variables.data.draftId).toBe(91)
  })
})
