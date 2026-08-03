import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { Storage } from '@/utils/storage'
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

  it('reinitializes the composer when the preferred draft id changes on the same route', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    Storage.set('noviis:draft:1:create:free:new:draft-91', {
      draftId: 91,
      boardUrl: 'free',
      title: 'First draft',
      contents: 'First body',
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
      hasLocalChanges: true,
    })
    Storage.set('noviis:draft:1:create:free:new:draft-92', {
      draftId: 92,
      boardUrl: 'free',
      title: 'Second draft',
      contents: 'Second body',
      clientModifiedAt: '2026-08-02T01:00:00.000Z',
      hasLocalChanges: true,
    })
    const wrapper = mountPostForm('create', {}, {}, { postId: '', initialDraftId: '91' })
    await flushPromises()
    expect(wrapper.get('#title').element).toHaveProperty('value', 'First draft')

    await wrapper.setProps({ initialDraftId: '92' })
    await flushPromises()

    expect(wrapper.get('#title').element).toHaveProperty('value', 'Second draft')
    expect(wrapper.get('[data-testid="editor-input"]').element).toHaveProperty('value', 'Second body')
  })

  it('keeps a restored server snapshot canonical instead of marking it locally changed', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const storageKey = 'noviis:draft:1:create:free:new'
    Storage.set(storageKey, {
      draftId: 91,
      boardUrl: 'free',
      title: 'Recovered draft',
      contents: 'Recovered body',
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
      hasLocalChanges: false,
    })

    mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()

    expect(Storage.get(storageKey)).toEqual(expect.objectContaining({
      title: 'Recovered draft',
      hasLocalChanges: false,
    }))
    expect(mockSaveDraftMutateAsync).not.toHaveBeenCalled()
  })

  it('flushes the latest input synchronously when the page is hidden', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const wrapper = mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()
    const title = wrapper.get('#title').element as HTMLInputElement
    title.value = 'Typed immediately before page hide'
    title.dispatchEvent(new Event('input', { bubbles: true }))

    window.dispatchEvent(new Event('pagehide'))

    expect(Storage.get('noviis:draft:1:create:free:new')).toEqual(expect.objectContaining({
      title: 'Typed immediately before page hide',
      hasLocalChanges: true,
    }))
  })

  it('does not turn a clean snapshot into a local edit on page hide', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const storageKey = 'noviis:draft:1:create:free:new'
    Storage.set(storageKey, {
      draftId: 91,
      boardUrl: 'free',
      title: 'Canonical draft',
      contents: 'Canonical body',
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
      hasLocalChanges: false,
    })
    mountPostForm('create')
    await flushPromises()

    window.dispatchEvent(new Event('pagehide'))

    expect(Storage.get(storageKey)).toEqual(expect.objectContaining({
      title: 'Canonical draft',
      hasLocalChanges: false,
    }))
  })
})
