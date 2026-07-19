import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import {
    boardRef,
    createMinimalEditorStub,
    createNoToolbarEditorStub,
    editorSetEmoticon,
    editorSetVideo,
    fillPostForm,
    findButtonByText,
    findEditorModeButtons,
    getLastCreatePostVariables,
    getLastUpdatePostVariables,
    isBoardLoadingRef,
    isCreatePendingRef,
    isUpdatePendingRef,
    mockAddToast,
    mockCreateMutate,
    mockCreateScheduledMutate,
    mockUpdateScheduledMutate,
    mockUpdateMutate,
    mockUsePostDetail,
    mountPostForm,
    postRef,
    scheduledPostRef,
    resetPostFormTestState,
    routeState,
    setBoardCategories,
    submitPostForm,
    unmountPostFormWrappers,
} from './PostFormTestHarness'

import logger from '@/utils/logger'
import { getExposedVm } from '@/test/vue-test-helpers'
import { whenPwaReloadSafe } from '@/pwaReloadGuard'

type PostFormExposed = {
    hasUnsavedChanges: () => boolean
    isSubmissionInProgress: () => boolean
    getLeaveConfirmMessage: () => string
}

describe('PostForm', () => {
    afterEach(() => {
        unmountPostFormWrappers()
    })

    beforeEach(() => {
        resetPostFormTestState()
    })

    it('opens the preview modal from the header action', async () => {
        const wrapper = mountPostForm('create')

        await wrapper.get('#title').setValue('Preview title')
        await wrapper.get('[data-testid="editor-input"]').setValue('<p>Preview body</p>')
        await findButtonByText(wrapper, 'board.writePost.actions.preview').trigger('click')
        await nextTick()

        expect(wrapper.text()).toContain('board.writePost.preview.title')
        expect(wrapper.text()).toContain('Preview title')
        expect(wrapper.text()).toContain('Preview body')
    })

    it('passes expected post detail ref and enabled option by mode', () => {
        mountPostForm('create')
        mountPostForm('edit')

        const createCall = mockUsePostDetail.mock.calls[0]
        const editCall = mockUsePostDetail.mock.calls[1]

        expect((createCall[0] as { value: string }).value).toBe('')
        expect((createCall[1] as { enabled: { value: boolean } }).enabled.value).toBe(false)
        expect((editCall[0] as { value: string }).value).toBe('1')
        expect((editCall[1] as { enabled: { value: boolean } }).enabled.value).toBe(true)
    })

    it('filters categories by role in create mode and keeps only selectable categories plus current one in edit mode', async () => {
        setBoardCategories([
            { categoryId: 1, name: 'General', minWriteRole: 'USER' },
            { categoryId: 2, name: 'Board Admin', minWriteRole: 'BOARD_ADMIN' },
            { categoryId: 3, name: 'Super Admin', minWriteRole: 'SUPER_ADMIN' },
            { categoryId: 4, name: 'Legacy', minWriteRole: 'ADMIN' },
        ])
        const createWrapper = mountPostForm('create')
        await nextTick()
        const createOptionTexts = createWrapper.findAll('option').map((option) => option.text())

        expect(createOptionTexts).toContain('General')
        expect(createOptionTexts).not.toContain('Board Admin')
        expect(createOptionTexts).not.toContain('Super Admin')
        expect(createOptionTexts).not.toContain('Legacy')

        postRef.value = {
            postId: 1,
            title: 'Edit me',
            contents: 'Edit content',
            category: { categoryId: 2, name: 'Board Admin', minWriteRole: 'BOARD_ADMIN' },
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }
        const editWrapper = mountPostForm('edit')
        await nextTick()
        const editOptionTexts = editWrapper.findAll('option').map((option) => option.text())

        expect(editOptionTexts).toContain('General')
        expect(editOptionTexts).toContain('Board Admin')
        expect(editOptionTexts).not.toContain('Super Admin')
        expect(editOptionTexts).not.toContain('Legacy')
        const currentCategoryOption = editWrapper.findAll('option').find((option) => option.text() === 'Board Admin')
        expect(currentCategoryOption?.attributes('disabled')).toBeDefined()
    })

    it('hydrates form from post detail in edit mode', async () => {
        routeState.params.postId = '22'
        postRef.value = {
            postId: 22,
            title: 'Existing post',
            contents: 'Existing content',
            category: { categoryId: 9 },
            tags: [{ name: 'tag-a' }, 'tag-b'],
            isNsfw: true,
            isSpoiler: true,
        }
        setBoardCategories([
            { categoryId: 9, name: 'Edit Category', minWriteRole: 'USER' },
            { categoryId: 1, name: 'General', minWriteRole: 'USER' },
        ])

        const wrapper = mountPostForm('edit')
        await nextTick()

        expect((wrapper.get('#title').element as HTMLInputElement).value).toBe('Existing post')
        expect((wrapper.get('#category').element as HTMLSelectElement).value).toBe('9')
        expect((wrapper.get('[data-testid=\"editor-input\"]').element as HTMLTextAreaElement).value).toBe('Existing content')
    })

    it('resets edit hydration state when the form identity changes', async () => {
        postRef.value = {
            postId: 1,
            title: 'First post',
            contents: 'First content',
            category: { categoryId: 1 },
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }
        const wrapper = mountPostForm('edit')
        await nextTick()

        expect((wrapper.get('#title').element as HTMLInputElement).value).toBe('First post')

        await wrapper.setProps({ postId: '2' })
        await nextTick()

        expect((wrapper.get('#title').element as HTMLInputElement).value).toBe('')

        postRef.value = {
            postId: 2,
            title: 'Second post',
            contents: 'Second content',
            category: { categoryId: 1 },
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }
        await nextTick()

        expect((wrapper.get('#title').element as HTMLInputElement).value).toBe('Second post')
        expect((wrapper.get('[data-testid=\"editor-input\"]').element as HTMLTextAreaElement).value).toBe('Second content')
    })

    it('validates required fields before submit', async () => {
        const wrapper = mountPostForm('create')

        await wrapper.get('form').trigger('submit')
        expect(mockAddToast).toHaveBeenCalledWith('board.writePost.validation', 'error')
        expect(mockCreateMutate).not.toHaveBeenCalled()

        await wrapper.get('#title').setValue('   ')
        await wrapper.get('form').trigger('submit')
        expect(mockAddToast).toHaveBeenCalledWith('board.writePost.validation', 'error')
        expect(mockCreateMutate).not.toHaveBeenCalled()

        setBoardCategories([])
        const wrapperNoCategory = mountPostForm('create')
        await wrapperNoCategory.get('#title').setValue('Title only')
        await wrapperNoCategory.get('form').trigger('submit')

        expect(mockAddToast).toHaveBeenCalledWith('board.writePost.validation', 'error')
        expect(mockCreateMutate).not.toHaveBeenCalled()
    })

    it('submits create payload and leaves success navigation to the parent view', async () => {
        boardRef.value = {
            allowNsfw: true,
            isAdmin: true,
            categories: [{ categoryId: 12, name: 'General', minWriteRole: 'USER' }],
        }
        const wrapper = mountPostForm('create')

        await fillPostForm(wrapper, {
            categoryId: '12',
            contents: 'Created body <img src="/api/v1/files/7"><img src="/files/8?download=true">',
            setTags: true,
            nsfw: true,
            spoiler: true,
        })
        await submitPostForm(wrapper)

        expect(mockCreateMutate).toHaveBeenCalledTimes(1)
        const [variables, options] = mockCreateMutate.mock.calls[0]

        expect(variables).toEqual({
            boardUrl: 'free',
            data: {
                title: 'Created title',
                categoryId: 12,
                tags: ['alpha', 'beta'],
                contents: 'Created body <img src="/api/v1/files/7"><img src="/api/v1/files/8?download=true">',
                isNsfw: true,
                isSpoiler: true,
                isSecret: false,
                isNotice: false,
                fileIds: [7, 8],
            },
        })

        options.onSuccess({ data: { data: { postId: 99 } } })
        expect(wrapper.emitted('cancel')).toBeUndefined()
    })

    it('normalizes local editor preview images before submit', async () => {
        setBoardCategories([{ categoryId: 12, name: 'General', minWriteRole: 'USER' }])
        const wrapper = mountPostForm('create')

        await fillPostForm(wrapper, {
            categoryId: '12',
            contents: '<p><img src="blob:https://noviis.kr/local" data-file-id="157" data-server-src="/api/v1/files/157"></p>',
        })
        await submitPostForm(wrapper)

        const variables = getLastCreatePostVariables()
        expect(variables.data.fileIds).toEqual([157])
        expect(variables.data.contents).toContain('src="/api/v1/files/157"')
        expect(variables.data.contents).not.toContain('blob:https://noviis.kr/local')
        expect(variables.data.contents).not.toContain('data-file-id')
    })

    it('submits poll data when creating a post', async () => {
        setBoardCategories([{ categoryId: 12, name: 'General', minWriteRole: 'USER' }])
        const wrapper = mountPostForm('create')

        await fillPostForm(wrapper, { categoryId: '12' })
        await wrapper.get('[data-testid="open-poll"]').trigger('click')
        await wrapper.get('[data-testid="poll-question"]').setValue('Best option?')
        await wrapper.get('[data-testid="poll-option-0"]').setValue('First')
        await wrapper.get('[data-testid="poll-option-1"]').setValue('Second')
        await wrapper.get('[data-testid="poll-multiple"]').setValue(true)
        await submitPostForm(wrapper)

        const variables = getLastCreatePostVariables()
        expect(variables.data.poll).toEqual({
            question: 'Best option?',
            options: ['First', 'Second'],
            multipleChoiceEnabled: true,
            anonymousEnabled: false,
            closesAt: null,
        })
    })

    it('does not open a new poll editor for a live post that cannot persist poll changes', async () => {
        postRef.value = {
            postId: 1,
            title: 'Existing post',
            contents: 'Existing content',
            category: null,
            tags: [],
            isNsfw: false,
            isSpoiler: false,
            poll: null,
        }
        const wrapper = mountPostForm('edit')
        await nextTick()

        await wrapper.get('[data-testid="open-poll"]').trigger('click')
        await nextTick()

        expect(wrapper.find('[data-testid="poll-question"]').exists()).toBe(false)
    })

    it('blocks publishing an incomplete poll instead of silently dropping it', async () => {
        const wrapper = mountPostForm('create')
        await fillPostForm(wrapper, { categoryId: '12' })
        await wrapper.get('[data-testid="open-poll"]').trigger('click')
        await wrapper.get('[data-testid="poll-question"]').setValue('Incomplete poll')
        await wrapper.get('[data-testid="poll-option-0"]').setValue('First')

        await submitPostForm(wrapper)

        expect(mockCreateMutate).not.toHaveBeenCalled()
        expect(mockAddToast).toHaveBeenCalledWith(
            'board.writePost.poll.validation.optionRequired',
            'error',
        )
    })

    it('blocks a scheduled poll that closes no later than one minute after publication', async () => {
        const wrapper = mountPostForm('create')
        await fillPostForm(wrapper, { categoryId: '12' })
        await wrapper.get('[data-testid="open-poll"]').trigger('click')
        await wrapper.get('[data-testid="poll-question"]').setValue('Scheduled poll')
        await wrapper.get('[data-testid="poll-option-0"]').setValue('First')
        await wrapper.get('[data-testid="poll-option-1"]').setValue('Second')
        await wrapper.get('#scheduled-at').setValue('2099-01-02T12:00')
        await wrapper.get('#poll-closes-at').setValue('2099-01-02T12:01')

        await submitPostForm(wrapper)

        expect(mockCreateMutate).not.toHaveBeenCalled()
        expect(mockCreateScheduledMutate).not.toHaveBeenCalled()
        expect(mockAddToast).toHaveBeenCalledWith(
            'board.writePost.poll.validation.closesAtAfterSchedule',
            'error',
        )
    })

    it('keeps redirectOnCreate as a compatibility prop without navigating internally', async () => {
        setBoardCategories([{ categoryId: 1, name: 'General', minWriteRole: 'USER' }])
        const wrapper = mountPostForm('create', {}, {}, { redirectOnCreate: '/inquiry' })

        await fillPostForm(wrapper)
        await submitPostForm(wrapper)

        const [, options] = mockCreateMutate.mock.calls[0]
        options.onSuccess({ data: { data: { postId: 101 } } })
        expect(wrapper.emitted('cancel')).toBeUndefined()
    })

    it('delegates create navigation to onSubmitted when provided', async () => {
        const onSubmitted = vi.fn()
        boardRef.value = {
            allowNsfw: false,
            isAdmin: false,
            categories: [{ categoryId: 1, name: 'General', minWriteRole: 'USER' }],
        }
        const wrapper = mountPostForm('create', {}, {}, { onSubmitted })

        await fillPostForm(wrapper)
        await submitPostForm(wrapper)

        const [, options] = mockCreateMutate.mock.calls[0]
        options.onSuccess({ data: { data: { postId: 103 } } })

        expect(onSubmitted).toHaveBeenCalledWith({
            mode: 'create',
            boardUrl: 'free',
            newPostId: 103,
            isSecret: false,
            isBoardAdmin: false,
        })
    })

    it('keeps goBackOnCreate as a compatibility prop without navigating internally', async () => {
        window.history.pushState({}, '', '/temp-inquiry')
        setBoardCategories([{ categoryId: 1, name: 'General', minWriteRole: 'USER' }])
        const wrapper = mountPostForm('create', {}, {}, {
            goBackOnCreate: true,
            redirectOnCreate: '/fallback',
        })

        await fillPostForm(wrapper)
        await submitPostForm(wrapper)

        const [, options] = mockCreateMutate.mock.calls[0]
        options.onSuccess({ data: { data: { postId: 102 } } })

        expect(wrapper.emitted('cancel')).toBeUndefined()
    })

    it('logs errors when create or update fails', async () => {
        const wrapperCreate = mountPostForm('create')
        await fillPostForm(wrapperCreate, { title: 'Create error case' })
        await submitPostForm(wrapperCreate)

        const [, createOptions] = mockCreateMutate.mock.calls[0]
        createOptions.onError(new Error('create failed'))
        expect(logger.error).toHaveBeenCalledWith('Failed to create post:', expect.any(Error))

        postRef.value = {
            postId: 1,
            title: 'Edit me',
            contents: 'Edit content',
            category: { categoryId: 1 },
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }
        const wrapperEdit = mountPostForm('edit')
        await nextTick()
        await wrapperEdit.get('#title').setValue('Edited title')
        await submitPostForm(wrapperEdit)

        const [, updateOptions] = mockUpdateMutate.mock.calls[0]
        updateOptions.onError(new Error('update failed'))
        expect(logger.error).toHaveBeenCalledWith('Failed to update post:', expect.any(Error))
    })

    it('submits update payload and leaves success navigation to the parent view', async () => {
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
        await wrapper.get('[data-testid=\"editor-input\"]').setValue('After body')
        await submitPostForm(wrapper)

        expect(mockUpdateMutate).toHaveBeenCalledTimes(1)
        const [variables, options] = mockUpdateMutate.mock.calls[0]

        expect(variables).toEqual({
            postId: '77',
            data: {
                title: 'After title',
                categoryId: 5,
                tags: ['before'],
                contents: 'After body',
                isNsfw: false,
                isSpoiler: false,
                isSecret: false,
                seriesId: null,
                fileIds: [],
            },
        })

        options.onSuccess()
        expect(wrapper.emitted('cancel')).toBeUndefined()
    })

    it('delegates update navigation to onSubmitted when provided', async () => {
        const onSubmitted = vi.fn()
        routeState.params.postId = '88'
        postRef.value = {
            postId: 88,
            title: 'Before title',
            contents: 'Before body',
            category: { categoryId: 5 },
            tags: ['before'],
            isNsfw: false,
            isSpoiler: false,
        }
        const wrapper = mountPostForm('edit', {}, {}, { onSubmitted })
        await nextTick()

        await wrapper.get('#title').setValue('After title')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue('After body')
        await submitPostForm(wrapper)

        const [, options] = mockUpdateMutate.mock.calls[0]
        options.onSuccess()

        expect(onSubmitted).toHaveBeenCalledWith({
            mode: 'edit',
            boardUrl: 'free',
            postId: '88',
            isSecret: false,
            isBoardAdmin: false,
        })
    })

    it('preserves existing edit image and attachment file ids from post content', async () => {
        routeState.params.postId = '78'
        postRef.value = {
            postId: 78,
            title: 'Before title',
            contents: '<p>Before <img src="/api/v1/files/31"><img src="/files/32?size=sm"><a href="/api/v1/files/99">file</a></p>',
            category: { categoryId: 5 },
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }
        const wrapper = mountPostForm('edit')
        await nextTick()

        await wrapper.get('#title').setValue('After title')
        await submitPostForm(wrapper)

        const variables = getLastUpdatePostVariables()
        expect(variables.data.fileIds).toEqual([31, 32, 99])
        expect(variables.data.contents).toContain('src="/api/v1/files/32?size=sm"')
    })

    it('keeps current edit category when it is no longer selectable', async () => {
        routeState.params.postId = '88'
        setBoardCategories([{ categoryId: 1, name: 'General', minWriteRole: 'USER' }])
        postRef.value = {
            postId: 88,
            title: 'Before title',
            contents: 'Before body',
            category: { categoryId: 9, name: 'Archived', minWriteRole: 'BOARD_ADMIN' },
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }

        const wrapper = mountPostForm('edit')
        await nextTick()

        const categoryOptions = wrapper.findAll('option')
        expect(categoryOptions.map((option) => option.text())).toContain('Archived')
        expect(categoryOptions.find((option) => option.text() === 'Archived')?.attributes('disabled')).toBeDefined()
        expect((wrapper.get('#category').element as HTMLSelectElement).value).toBe('9')

        await wrapper.get('#title').setValue('After title')
        await submitPostForm(wrapper)

        const variables = getLastUpdatePostVariables()
        expect(variables.data.categoryId).toBe(9)
    })

    it('handles video popover and emoticon insertion', async () => {
        const wrapper = mountPostForm('create')

        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')
        expect(wrapper.find('.video-url-popover').exists()).toBe(true)

        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(mockAddToast).toHaveBeenCalledWith('board.writePost.videoUrlRequired', 'error')
        expect(editorSetVideo).not.toHaveBeenCalled()

        await wrapper.get('#post-video-url-input').setValue('https://example.com/video')
        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(mockAddToast).toHaveBeenLastCalledWith('board.writePost.invalidVideoUrl', 'error')
        expect(editorSetVideo).not.toHaveBeenCalled()

        await wrapper.get('#post-video-url-input').setValue('https://youtu.be/abc123')
        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(editorSetVideo).toHaveBeenCalledWith('https://www.youtube.com/embed/abc123?showinfo=0')
        expect(wrapper.find('.video-url-popover').exists()).toBe(false)

        await wrapper.get('[data-testid=\"open-emoticon\"]').trigger('click')
        await wrapper.get('[data-testid=\"pick-emoticon\"]').trigger('click')
        expect(editorSetEmoticon).toHaveBeenCalledWith({ imageUrl: '/emoticon.png' })

        await wrapper.get('[data-testid=\"open-emoticon\"]').trigger('click')
        await wrapper.get('[data-testid=\"close-emoticon\"]').trigger('click')
        expect(wrapper.find('[data-testid=\"pick-emoticon\"]').exists()).toBe(false)
    })

    it('converts vimeo URL and uses mobile-centered popover position', async () => {
        vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({
            matches: query === '(max-width: 767px)',
            media: query,
            onchange: null,
            addListener: vi.fn(),
            removeListener: vi.fn(),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn(),
        }) as unknown as MediaQueryList)

        const wrapper = mountPostForm('create')
        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')
        const popover = wrapper.get('.video-url-popover')
        const expectedTop = `${window.innerHeight / 2}px`
        const expectedLeft = `${window.innerWidth / 2}px`

        expect(popover.attributes('style')).toContain(`top: ${expectedTop}`)
        expect(popover.attributes('style')).toContain(`left: ${expectedLeft}`)

        await wrapper.get('#post-video-url-input').setValue('vimeo.com/12345')
        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(editorSetVideo).toHaveBeenCalledWith('https://player.vimeo.com/video/12345')
    })

    it('supports keyboard shortcuts and dirty-state helpers', async () => {
        const wrapper = mountPostForm('create')
        await nextTick()

        await wrapper.get('#title').setValue('Shortcut title')
        await wrapper.get('#category').setValue('1')
        await nextTick()

        const ctrlEnterEvent = new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, cancelable: true })
        document.dispatchEvent(ctrlEnterEvent)
        expect(mockCreateMutate).toHaveBeenCalledTimes(1)

        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')
        const documentKeydown = vi.fn()
        document.addEventListener('keydown', documentKeydown)
        const inputEscape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
        wrapper.get('#post-video-url-input').element.dispatchEvent(inputEscape)
        await nextTick()
        expect(wrapper.find('.video-url-popover').exists()).toBe(false)
        expect(wrapper.emitted('cancel')).toBeUndefined()
        expect(documentKeydown).not.toHaveBeenCalled()
        document.removeEventListener('keydown', documentKeydown)

        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')
        const escapeForVideo = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true })
        document.dispatchEvent(escapeForVideo)
        await nextTick()
        expect(wrapper.find('.video-url-popover').exists()).toBe(false)

        await wrapper.get('[data-testid=\"open-emoticon\"]').trigger('click')
        const escapeForEmoticon = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true })
        document.dispatchEvent(escapeForEmoticon)
        await nextTick()
        expect(wrapper.find('[data-testid=\"pick-emoticon\"]').exists()).toBe(false)

        const escapeForCancel = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true })
        document.dispatchEvent(escapeForCancel)
        expect(wrapper.emitted('cancel')).toBeUndefined()

        const exposed = getExposedVm<PostFormExposed>(wrapper)
        expect(exposed.hasUnsavedChanges()).toBe(true)
        expect(exposed.isSubmissionInProgress()).toBe(true)
        expect(exposed.getLeaveConfirmMessage()).toBe('board.writePost.leaveConfirm')

        const beforeUnloadEvent = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
        window.dispatchEvent(beforeUnloadEvent)
        expect(beforeUnloadEvent.defaultPrevented).toBe(true)
    })

    it('defers a PWA reload while the post form has unsaved changes', async () => {
        const wrapper = mountPostForm('create')
        const reload = vi.fn()

        await nextTick()
        await wrapper.get('#title').setValue('Unsaved post')
        await nextTick()

        expect(whenPwaReloadSafe(reload)).toBe(false)
        expect(reload).not.toHaveBeenCalled()

        unmountPostFormWrappers()

        expect(reload).toHaveBeenCalledTimes(1)
    })

    it('shows spinner while loading', () => {
        isBoardLoadingRef.value = true
        const wrapper = mountPostForm('create')

        expect(wrapper.text()).toContain('loading')
    })

    it('covers unsaved-change helper branches for empty snapshot, category and tag changes', async () => {
        setBoardCategories([])
        const noCategoryWrapper = mountPostForm('create')
        const noCategoryExposed = getExposedVm<Pick<PostFormExposed, 'hasUnsavedChanges'>>(noCategoryWrapper)
        expect(noCategoryExposed.hasUnsavedChanges()).toBe(false)

        setBoardCategories([
            { categoryId: 1, name: 'General', minWriteRole: 'USER' },
            { categoryId: 2, name: 'Second', minWriteRole: 'USER' },
        ])
        const categoryDirtyWrapper = mountPostForm('create')
        await nextTick()
        await categoryDirtyWrapper.get('#category').setValue('2')
        const categoryDirtyExposed = getExposedVm<Pick<PostFormExposed, 'hasUnsavedChanges'>>(categoryDirtyWrapper)
        expect(categoryDirtyExposed.hasUnsavedChanges()).toBe(true)

        const tagsLengthDirtyWrapper = mountPostForm('create')
        await nextTick()
        await tagsLengthDirtyWrapper.get('[data-testid=\"set-tags\"]').trigger('click')
        const tagsLengthDirtyExposed = getExposedVm<Pick<PostFormExposed, 'hasUnsavedChanges'>>(tagsLengthDirtyWrapper)
        expect(tagsLengthDirtyExposed.hasUnsavedChanges()).toBe(true)

        postRef.value = {
            postId: 77,
            title: 'Tag compare',
            contents: 'Same length',
            category: { categoryId: 1 },
            tags: ['alpha', 'gamma'],
            isNsfw: false,
            isSpoiler: false,
        }
        const tagsValueDirtyWrapper = mountPostForm('edit')
        await nextTick()
        await tagsValueDirtyWrapper.get('[data-testid=\"set-tags\"]').trigger('click')
        const tagsValueDirtyExposed = getExposedVm<Pick<PostFormExposed, 'hasUnsavedChanges'>>(tagsValueDirtyWrapper)
        expect(tagsValueDirtyExposed.hasUnsavedChanges()).toBe(true)
    })

    it('shows submitting labels for create and edit pending states', async () => {
        isCreatePendingRef.value = true
        const createWrapper = mountPostForm('create')
        await nextTick()
        expect(createWrapper.text()).toContain('board.writePost.submitting')
        expect(createWrapper.get('form fieldset').attributes()).toMatchObject({
            disabled: '',
            inert: 'true',
            'aria-busy': 'true',
        })

        isCreatePendingRef.value = false
        isUpdatePendingRef.value = true
        postRef.value = {
            postId: 5,
            title: 'Pending edit',
            contents: 'pending',
            category: { categoryId: 1 },
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }
        const editWrapper = mountPostForm('edit')
        await nextTick()
        expect(editWrapper.text()).toContain('board.writePost.updating')
    })

    it('locks every composer field after a submit starts', async () => {
        const wrapper = mountPostForm('create')
        await fillPostForm(wrapper)

        await submitPostForm(wrapper)

        expect(mockCreateMutate).toHaveBeenCalledOnce()
        expect(wrapper.get('form fieldset').attributes()).toMatchObject({
            disabled: '',
            inert: 'true',
            'aria-busy': 'true',
        })
        const cancelButtons = wrapper.findAll('button')
            .filter((button) => button.text().includes('common.cancel'))
        expect(cancelButtons).not.toHaveLength(0)
        expect(cancelButtons.every((button) => button.attributes('disabled') !== undefined)).toBe(true)
        await cancelButtons[0]?.trigger('click')
        expect(wrapper.emitted('cancel')).toBeUndefined()
        expect((wrapper.vm as unknown as { isSubmissionInProgress: () => boolean }).isSubmissionInProgress()).toBe(true)

        const unloadEvent = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
        window.dispatchEvent(unloadEvent)
        expect(unloadEvent.defaultPrevented).toBe(true)
    })

    it('uses wrapper fallback popover position and rejects unsupported video URLs', async () => {
        vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({
            matches: false,
            media: query,
            onchange: null,
            addListener: vi.fn(),
            removeListener: vi.fn(),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn(),
        }) as unknown as MediaQueryList)

        const wrapper = mountPostForm('create', { PostEditorTipTap: createNoToolbarEditorStub() })
        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')

        const popover = wrapper.get('.video-url-popover')
        expect(popover.attributes('style')).toContain('top: 60px')
        expect(popover.attributes('style')).toContain('left: 0px')

        await wrapper.get('#post-video-url-input').setValue('  https://example.com/video  ')
        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(editorSetVideo).not.toHaveBeenCalled()
        expect(mockAddToast).toHaveBeenLastCalledWith('board.writePost.invalidVideoUrl', 'error')
    })

    it('updates mobile/desktop checkboxes and uses fallback strings when translation is empty', async () => {
        boardRef.value = {
            allowNsfw: true,
            isAdmin: true,
            categories: [{ categoryId: 1, name: 'General', minWriteRole: 'USER' }],
        }
        const wrapper = mountPostForm('create', {}, { $t: () => '' })

        await wrapper.get('#isNotice-m').setValue(true)
        await wrapper.get('#nsfw-m').setValue(true)
        await wrapper.get('#spoiler-m').setValue(true)
        await wrapper.get('#secret-m').setValue(true)
        await wrapper.get('#isNotice').setValue(false)
        await wrapper.get('#secret').setValue(false)

        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')
        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')

        const lastToastArgs = mockAddToast.mock.calls.at(-1)
        expect(lastToastArgs?.[0]).not.toBe('')
        expect(lastToastArgs?.[1]).toBe('error')
    })

    it('omits categoryId when edit payload category is empty string', async () => {
        routeState.params.postId = '31'
        postRef.value = {
            postId: 31,
            title: 'No category',
            contents: 'body',
            category: null,
            tags: [],
            isNsfw: false,
            isSpoiler: false,
        }

        const wrapper = mountPostForm('edit')
        await nextTick()
        await wrapper.get('#title').setValue('No category')
        await submitPostForm(wrapper)

        const variables = getLastUpdatePostVariables()
        expect(variables.data).not.toHaveProperty('categoryId')
    })

    it('submits with empty fileIds when editor does not expose fileIds ref', async () => {
        const wrapper = mountPostForm('create', { PostEditorTipTap: createMinimalEditorStub() })
        await fillPostForm(wrapper, { title: 'No files', contents: 'Body' })
        await submitPostForm(wrapper)

        const variables = getLastCreatePostVariables()
        expect(variables.data.fileIds).toEqual([])
    })

    it('hydrates and updates every editable scheduled-post field', async () => {
        boardRef.value = {
            allowNsfw: true,
            isAdmin: true,
            categories: [{ categoryId: 3, name: 'Scheduled', minWriteRole: 'USER' }],
        }
        scheduledPostRef.value = {
            scheduledPostId: 44,
            boardUrl: 'free',
            categoryId: 3,
            title: 'Scheduled title',
            contents: '<p>Scheduled body<img src="/api/v1/files/10"></p>',
            tags: ['alpha'],
            isNotice: true,
            isNsfw: true,
            isSpoiler: true,
            isSecret: true,
            fileIds: [10],
            draftId: 5,
            poll: {
                question: 'Scheduled question',
                options: [{ optionText: 'First' }, { optionText: 'Second' }],
                multipleChoiceEnabled: false,
                anonymousEnabled: true,
                closesAt: '2026-07-21T12:00',
            },
            seriesId: null,
            scheduledAt: '2026-07-20T12:00',
        }

        const wrapper = mountPostForm('edit', {}, {}, { scheduledPostId: '44', postId: '' })
        await nextTick()

        expect(wrapper.get('#title').element).toHaveProperty('value', 'Scheduled title')
        expect(wrapper.get('[data-testid="editor-input"]').element).toHaveProperty(
            'value',
            '<p>Scheduled body<img src="/api/v1/files/10"></p>',
        )
        expect(wrapper.get('#scheduled-at').element).toHaveProperty('value', '2026-07-20T12:00')

        await wrapper.get('#title').setValue('Updated schedule')
        await submitPostForm(wrapper)

        expect(mockUpdateScheduledMutate).toHaveBeenCalledWith({
            scheduledPostId: '44',
            data: expect.objectContaining({
                title: 'Updated schedule',
                contents: '<p>Scheduled body<img src="/api/v1/files/10"></p>',
                categoryId: 3,
                tags: ['alpha'],
                isNotice: true,
                isNsfw: true,
                isSpoiler: true,
                isSecret: true,
                fileIds: [10],
                draftId: 5,
                scheduledAt: '2026-07-20T12:00',
                poll: {
                    question: 'Scheduled question',
                    options: ['First', 'Second'],
                    multipleChoiceEnabled: false,
                    anonymousEnabled: true,
                    closesAt: '2026-07-21T12:00',
                },
            }),
        }, expect.any(Object))
        expect(mockUpdateMutate).not.toHaveBeenCalled()
    })
})
