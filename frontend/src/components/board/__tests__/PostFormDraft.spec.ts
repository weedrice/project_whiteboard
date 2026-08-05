import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

  it('removes invalid server-reset file references from the draft content', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const contents = '<p>Kept body</p><img src="/api/v1/files/7" data-file-id="7">'
    mockSaveDraftMutateAsync.mockResolvedValueOnce({
      data: {
        data: {
          draftId: 91,
          version: 1,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft title',
          contents,
          tags: [],
          fileIds: [],
          seriesId: null,
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          staleReferencesReset: true,
          updatedAt: '2026-08-05T00:00:00.000Z',
        },
      },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()

    await wrapper.get('#title').setValue('Draft title')
    await wrapper.get('[data-testid="editor-input"]').setValue(contents)
    ;(wrapper.vm as unknown as { handleEditorFileUploaded: (fileId: number) => void })
      .handleEditorFileUploaded(7)
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockSaveDraftMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ fileIds: [7] }))
    expect(wrapper.get('[data-testid="editor-input"]').element).toHaveProperty('value', '<p>Kept body</p>')
  })

  it('removes a legacy content reference that was already absent from draft fileIds', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const contents = '<p>Kept body</p><img src="/api/v1/files/7">'
    mockSaveDraftMutateAsync.mockResolvedValueOnce({
      data: {
        data: {
          draftId: 91,
          version: 1,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft title',
          contents: '<p>Kept body</p>',
          tags: [],
          fileIds: [],
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          staleReferencesReset: true,
          updatedAt: '2026-08-05T00:00:00.000Z',
        },
      },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()

    await wrapper.get('#title').setValue('Draft title')
    await wrapper.get('[data-testid="editor-input"]').setValue(contents)
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockSaveDraftMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ fileIds: [] }))
    expect(wrapper.get('[data-testid="editor-input"]').element).toHaveProperty('value', '<p>Kept body</p>')
  })

  it('preserves a file uploaded while stale references are being recovered', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const firstResponse = {
      data: {
        data: {
          draftId: 91,
          version: 1,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft title',
          contents: '<img src="/api/v1/files/7">',
          tags: [],
          fileIds: [7],
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          staleReferencesReset: true,
          updatedAt: '2026-08-05T00:00:00.000Z',
        },
      },
    }
    let resolveFirstSave!: (response: typeof firstResponse) => void
    mockSaveDraftMutateAsync
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirstSave = resolve }))
      .mockResolvedValueOnce({
        data: {
          data: {
            ...firstResponse.data.data,
            version: 2,
            contents: '<img src="/api/v1/files/7"><img src="/api/v1/files/8">',
            fileIds: [7, 8],
            staleReferencesReset: false,
            updatedAt: '2026-08-05T00:00:01.000Z',
          },
        },
      })
    const wrapper = mountPostForm('create')
    await flushPromises()
    await wrapper.get('#title').setValue('Draft title')
    await wrapper.get('[data-testid="editor-input"]').setValue('<img src="/api/v1/files/7">')
    ;(wrapper.vm as unknown as { handleEditorFileUploaded: (fileId: number) => void })
      .handleEditorFileUploaded(7)

    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await nextTick()
    await wrapper.get('[data-testid="editor-input"]')
      .setValue('<img src="/api/v1/files/7"><img src="/api/v1/files/8">')
    ;(wrapper.vm as unknown as { handleEditorFileUploaded: (fileId: number) => void })
      .handleEditorFileUploaded(8)
    resolveFirstSave(firstResponse)
    await flushPromises()

    expect(mockSaveDraftMutateAsync).toHaveBeenCalledTimes(2)
    expect(mockSaveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
      fileIds: [7, 8],
      contents: '<img src="/api/v1/files/7"><img src="/api/v1/files/8">',
    }))
  })

  it('warns when saving a draft evicts older drafts over the account limit', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()
    mockSaveDraftMutateAsync.mockResolvedValueOnce({
      data: {
        data: {
          draftId: 91,
          version: 1,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft title',
          contents: '',
          tags: [],
          fileIds: [],
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          evictedDraftCount: 2,
          updatedAt: '2026-08-05T00:00:00.000Z',
        },
      },
    })

    await wrapper.get('#title').setValue('Draft title')
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockAddToast).toHaveBeenCalledWith(
      'board.writePost.draftStatus.limitEvicted',
      'warning',
    )
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
    expect(wrapper.text()).toContain('board.writePost.draftStatus.retryNow')
  })

  it('shows retry guidance instead of a failure when reference recovery is scheduled', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const contents = '<p>Draft body</p><img src="/api/v1/files/7">'
    mockSaveDraftMutateAsync.mockResolvedValue({
      data: {
        data: {
          draftId: 91,
          version: 1,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft title',
          contents,
          tags: [],
          fileIds: [7],
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          staleReferencesReset: true,
          updatedAt: '2026-08-05T00:00:00.000Z',
        },
      },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()

    await wrapper.get('#title').setValue('Draft title')
    await wrapper.get('[data-testid="editor-input"]').setValue(contents)
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockSaveDraftMutateAsync).toHaveBeenCalledTimes(3)
    expect(mockAddToast).toHaveBeenCalledWith(
      'board.writePost.draftStatus.retryScheduled',
      'info',
    )
    expect(mockAddToast).not.toHaveBeenCalledWith('common.messages.saveFailed', 'error')
  })

  it('shows local storage failure ahead of the deleted draft state', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    mockSaveDraftMutateAsync.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 404, data: { error: { code: 'P007' } } },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()
    await wrapper.get('#title').setValue('Memory-only recovery')
    const setItem = vi.spyOn(Storage, 'setWithResult')
      .mockReturnValue({ ok: false, reason: 'quota-exceeded' })

    try {
      await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('board.writePost.draftStatus.localStorageFailed')
    } finally {
      setItem.mockRestore()
    }
  })

  it('offers scheduled post management when the draft becomes protected', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    mockSaveDraftMutateAsync.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 409, data: { error: { code: 'P005' } } },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()

    await wrapper.get('#title').setValue('Protected draft')
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('board.writePost.draftStatus.openScheduledPosts')
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

  it('rejects a draft title containing server-forbidden HTML before saving', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const wrapper = mountPostForm('create')
    await flushPromises()

    await wrapper.get('#title').setValue('<b>Draft title</b>')
    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(mockSaveDraftMutateAsync).not.toHaveBeenCalled()
    expect(mockAddToast).toHaveBeenCalledWith('board.writePost.validation', 'error')
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

    expect(Storage.get(`${storageKey}:draft-91`)).toEqual(expect.objectContaining({
      title: 'Recovered draft',
      hasLocalChanges: false,
    }))
    expect(Storage.has(storageKey)).toBe(false)
    expect(mockSaveDraftMutateAsync).not.toHaveBeenCalled()
  })

  it('resets an unavailable draft category to the first writable category', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    setBoardCategories([{ categoryId: 12, name: 'General', minWriteRole: 'USER' }])
    const storageKey = 'noviis:draft:1:create:free:new'
    Storage.set(storageKey, {
      draftId: 91,
      boardUrl: 'free',
      title: 'Recovered draft',
      contents: 'Recovered body',
      categoryId: 99,
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
      hasLocalChanges: true,
    })

    const wrapper = mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()

    expect(wrapper.get('#category').element).toHaveProperty('value', '12')
    expect(Storage.get(`${storageKey}:draft-91`)).toEqual(expect.objectContaining({
      categoryId: 12,
      staleReferencesReset: true,
    }))
    expect(Storage.has(storageKey)).toBe(false)
    expect(mockAddToast).toHaveBeenCalledWith(
      'board.writePost.draftStatus.referencesReset',
      'warning',
    )
  })

  it('resets a category removed while the draft editor is open', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    setBoardCategories([
      { categoryId: 12, name: 'General', minWriteRole: 'USER' },
      { categoryId: 99, name: 'Removed soon', minWriteRole: 'USER' },
    ])
    const wrapper = mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()
    await wrapper.get('#title').setValue('Draft with category')
    await wrapper.get('#category').setValue('99')
    setBoardCategories([{ categoryId: 12, name: 'General', minWriteRole: 'USER' }])
    mockSaveDraftMutateAsync.mockResolvedValueOnce({
      data: {
        data: {
          draftId: 91,
          version: 1,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft with category',
          contents: '',
          categoryId: null,
          tags: [],
          fileIds: [],
          seriesId: null,
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          staleReferencesReset: true,
          updatedAt: '2026-08-05T00:00:00.000Z',
        },
      },
    }).mockResolvedValueOnce({
      data: {
        data: {
          draftId: 91,
          version: 2,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft with category',
          contents: '',
          categoryId: 12,
          tags: [],
          fileIds: [],
          seriesId: null,
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          staleReferencesReset: false,
          updatedAt: '2026-08-05T00:00:01.000Z',
        },
      },
    })

    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(wrapper.get('#category').element).toHaveProperty('value', '12')
    expect(mockSaveDraftMutateAsync).toHaveBeenCalledTimes(2)
    expect(mockSaveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
      draftId: 91,
      version: 1,
      categoryId: 12,
    }))
    expect(Storage.get('noviis:draft:1:create:free:new:draft-91')).toEqual(expect.objectContaining({
      categoryId: 12,
      version: 2,
      hasLocalChanges: false,
    }))
    expect(mockAddToast).toHaveBeenCalledWith(
      'board.writePost.draftStatus.referencesReset',
      'warning',
    )
  })

  it('re-resolves the fallback when categories change again during recovery', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    setBoardCategories([
      { categoryId: 12, name: 'First fallback', minWriteRole: 'USER' },
      { categoryId: 99, name: 'Removed category', minWriteRole: 'USER' },
    ])
    const wrapper = mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()
    await wrapper.get('#title').setValue('Draft with changing category')
    await wrapper.get('#category').setValue('99')
    setBoardCategories([{ categoryId: 12, name: 'First fallback', minWriteRole: 'USER' }])

    const draftResponse = (version: number, categoryId: number | null, staleReferencesReset: boolean) => ({
      data: {
        data: {
          draftId: 91,
          version,
          boardId: 1,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Draft with changing category',
          contents: '',
          categoryId,
          tags: [],
          fileIds: [],
          seriesId: null,
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
          isSecret: false,
          staleReferencesReset,
          updatedAt: `2026-08-05T00:00:0${version}.000Z`,
        },
      },
    })
    mockSaveDraftMutateAsync
      .mockResolvedValueOnce(draftResponse(1, null, true))
      .mockImplementationOnce(async () => {
        setBoardCategories([{ categoryId: 13, name: 'Second fallback', minWriteRole: 'USER' }])
        return draftResponse(2, null, true)
      })
      .mockResolvedValueOnce(draftResponse(3, 13, false))

    await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
    await flushPromises()

    expect(wrapper.get('#category').element).toHaveProperty('value', '13')
    expect(mockSaveDraftMutateAsync).toHaveBeenCalledTimes(3)
    expect(mockSaveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
      draftId: 91,
      version: 2,
      categoryId: 13,
    }))
    expect(Storage.get('noviis:draft:1:create:free:new:draft-91')).toEqual(expect.objectContaining({
      categoryId: 13,
      version: 3,
      hasLocalChanges: false,
    }))
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

  it('retries a failed local snapshot when the page is hidden', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const wrapper = mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()
    const originalSet = Storage.setWithResult.bind(Storage)
    const setItem = vi.spyOn(Storage, 'setWithResult')
      .mockReturnValueOnce({ ok: false, reason: 'unavailable' })
      .mockImplementation(originalSet)

    try {
      await wrapper.get('#title').setValue('Retry this local snapshot')

      window.dispatchEvent(new Event('pagehide'))

      expect(setItem).toHaveBeenCalledTimes(2)
      expect(Storage.get('noviis:draft:1:create:free:new')).toEqual(expect.objectContaining({
        title: 'Retry this local snapshot',
        hasLocalChanges: true,
      }))
    } finally {
      setItem.mockRestore()
    }
  })

  it('flushes the latest input synchronously before a SPA unmount', async () => {
    mockPostFormAuthStore({
      isAuthenticated: true,
      user: { userId: 1, role: 'USER' },
    })
    const wrapper = mountPostForm('create', {}, {}, { postId: '' })
    await flushPromises()
    const title = wrapper.get('#title').element as HTMLInputElement
    title.value = 'Typed immediately before route change'
    title.dispatchEvent(new Event('input', { bubbles: true }))

    wrapper.unmount()

    expect(Storage.get('noviis:draft:1:create:free:new')).toEqual(expect.objectContaining({
      title: 'Typed immediately before route change',
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
