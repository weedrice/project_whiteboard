import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { EmoticonImage } from '@/types/emoticon'
import PostForm from '../PostForm.vue'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/composables/usePost'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/composables/useBoard', () => ({
    useBoard: vi.fn(),
}))

vi.mock('@/composables/usePost', () => ({
    usePost: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: vi.fn(),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: vi.fn(),
}))

vi.mock('@/api', () => ({ default: { post: vi.fn() } }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn() } }))

const editorSetVideo = vi.fn()
const editorSetEmoticon = vi.fn()
const editorFileIds = ref<number[]>([])

const BaseInputStub = defineComponent({
    name: 'BaseInput',
    props: {
        id: { type: String, default: '' },
        modelValue: { type: String, default: '' },
        type: { type: String, default: 'text' },
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () =>
            h('input', {
                id: props.id,
                type: props.type,
                value: props.modelValue,
                onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
            })
    },
})

const BaseSelectStub = defineComponent({
    name: 'BaseSelect',
    props: {
        id: { type: String, default: '' },
        modelValue: { type: [String, Number], default: '' },
    },
    emits: ['update:modelValue'],
    setup(props, { emit, slots }) {
        return () =>
            h(
                'select',
                {
                    id: props.id,
                    value: props.modelValue as string | number,
                    onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
                },
                slots.default?.(),
            )
    },
})

const BaseCheckboxStub = defineComponent({
    name: 'BaseCheckbox',
    props: {
        id: { type: String, default: '' },
        modelValue: { type: Boolean, default: false },
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () =>
            h('input', {
                id: props.id,
                type: 'checkbox',
                checked: props.modelValue,
                onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).checked),
            })
    },
})

const BaseButtonStub = defineComponent({
    name: 'BaseButton',
    props: {
        type: { type: String, default: 'button' },
        disabled: { type: Boolean, default: false },
    },
    emits: ['click'],
    setup(props, { emit, slots }) {
        return () =>
            h(
                'button',
                {
                    type: props.type,
                    disabled: props.disabled,
                    onClick: () => emit('click'),
                },
                slots.default?.(),
            )
    },
})

const PostTagsStub = defineComponent({
    name: 'PostTags',
    props: {
        inputId: { type: String, default: 'post-tags-input' },
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
        return () =>
            h('div', [
                h('label', { for: props.inputId }, 'tags'),
                h('input', { id: props.inputId }),
                h(
                    'button',
                    {
                        type: 'button',
                        'data-testid': 'set-tags',
                        onClick: () => emit('update:modelValue', ['alpha', 'beta']),
                    },
                    'set-tags',
                ),
            ])
    },
})

const EmoticonPickerStub = defineComponent({
    name: 'EmoticonPicker',
    props: {
        show: { type: Boolean, default: false },
    },
    emits: ['select', 'close'],
    setup(props, { emit }) {
        return () =>
            h('div', [
                props.show
                    ? h(
                        'button',
                        {
                            type: 'button',
                            'data-testid': 'pick-emoticon',
                            onClick: () => emit('select', { imageUrl: '/emoticon.png' } as EmoticonImage),
                        },
                        'pick',
                    )
                    : null,
                h(
                    'button',
                    {
                        type: 'button',
                        'data-testid': 'close-emoticon',
                        onClick: () => emit('close'),
                    },
                    'close',
                ),
            ])
    },
})

const PostEditorTipTapStub = defineComponent({
    name: 'PostEditorTipTap',
    props: {
        modelValue: { type: String, default: '' },
    },
    emits: ['update:modelValue', 'open-video', 'open-emoticon', 'file-uploaded'],
    setup(props, { emit, expose }) {
        expose({
            setVideo: (url: string) => editorSetVideo(url),
            setEmoticon: (image: EmoticonImage) => editorSetEmoticon(image),
            fileIds: { value: editorFileIds.value },
        })

        return () =>
            h('div', [
                h('div', { class: 'tiptap-toolbar' }, 'toolbar'),
                h('textarea', {
                    'data-testid': 'editor-input',
                    value: props.modelValue,
                    onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
                }),
                h(
                    'button',
                    {
                        type: 'button',
                        'data-testid': 'open-video',
                        onClick: () => emit('open-video'),
                    },
                    'open-video',
                ),
                h(
                    'button',
                    {
                        type: 'button',
                        'data-testid': 'open-emoticon',
                        onClick: () => emit('open-emoticon'),
                    },
                    'open-emoticon',
                ),
            ])
    },
})

const BaseSpinnerStub = defineComponent({
    name: 'BaseSpinner',
    setup() {
        return () => h('div', 'loading')
    },
})

const BaseModalStub = defineComponent({
    name: 'BaseModal',
    props: {
        isOpen: { type: Boolean, default: false },
        title: { type: String, default: '' },
    },
    setup(props, { slots }) {
        return () =>
            props.isOpen
                ? h('section', [h('h2', props.title), slots.default?.(), slots.footer?.()])
                : null
    },
})

const stubs = {
    BaseInput: BaseInputStub,
    BaseSelect: BaseSelectStub,
    BaseCheckbox: BaseCheckboxStub,
    BaseButton: BaseButtonStub,
    BaseModal: BaseModalStub,
    BaseSpinner: BaseSpinnerStub,
    PostTags: PostTagsStub,
    EmoticonPicker: EmoticonPickerStub,
    PostEditorTipTap: PostEditorTipTapStub,
    Teleport: true,
}

const mockAddToast = vi.fn()
const mockCreateMutate = vi.fn()
const mockUpdateMutate = vi.fn()
const mockUsePostDetail = vi.fn()
const mockUseCreatePost = vi.fn()
const mockUseUpdatePost = vi.fn()
const mockSaveDraftMutateAsync = vi.fn()
const mockDeleteDraftMutateAsync = vi.fn()
const isSaveDraftPendingRef = ref(false)
const isDeleteDraftPendingRef = ref(false)

const routeState = {
    params: {
        boardUrl: 'free',
        postId: '1',
    },
}

type TestCategory = { categoryId: number; name: string; minWriteRole?: string }
type TestBoard = { allowNsfw?: boolean; isAdmin?: boolean; categories?: TestCategory[] }

const boardRef = ref<TestBoard | null>({ allowNsfw: false, isAdmin: false, categories: [] })
const postRef = ref<any>(null)
const isBoardLoadingRef = ref(false)
const isPostLoadingRef = ref(false)
const isCreatePendingRef = ref(false)
const isUpdatePendingRef = ref(false)
const mountedWrappers: Array<ReturnType<typeof mount>> = []

const mountPostForm = (
    mode: 'create' | 'edit',
    overrideStubs: Record<string, unknown> = {},
    overrideMocks: { $t?: (key: string) => string } = {},
    props: Record<string, unknown> = {},
) => {
    const wrapper = mount(PostForm, {
        props: {
            mode,
            boardUrl: routeState.params.boardUrl,
            postId: routeState.params.postId,
            ...props,
        },
        global: {
            mocks: {
                $t: overrideMocks.$t ?? ((key: string) => key),
            },
            stubs: {
                ...stubs,
                ...overrideStubs,
            },
        },
    })
    mountedWrappers.push(wrapper)
    return wrapper
}

const setBoardCategories = (categories: TestCategory[]) => {
    boardRef.value = {
        ...(boardRef.value ?? {}),
        categories,
    }
}

const findButtonByText = (wrapper: ReturnType<typeof mount>, text: string) => {
    const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
    if (!button) {
        throw new Error(`Button not found: ${text}`)
    }
    return button
}

describe('PostForm', () => {
    afterEach(() => {
        while (mountedWrappers.length > 0) {
            mountedWrappers.pop()?.unmount()
        }
    })

    beforeEach(() => {
        vi.clearAllMocks()
        editorSetVideo.mockReset()
        editorSetEmoticon.mockReset()
        editorFileIds.value = []

        routeState.params.boardUrl = 'free'
        routeState.params.postId = '1'

        boardRef.value = {
            allowNsfw: false,
            isAdmin: false,
            categories: [{ categoryId: 1, name: 'General', minWriteRole: 'USER' }],
        }
        postRef.value = null
        isBoardLoadingRef.value = false
        isPostLoadingRef.value = false
        isCreatePendingRef.value = false
        isUpdatePendingRef.value = false

        vi.mocked(useAuthStore).mockReturnValue({ user: { role: 'USER' } } as any)
        vi.mocked(useToastStore).mockReturnValue({ addToast: mockAddToast } as any)

        vi.mocked(useBoard).mockReturnValue({
            useBoardDetail: () => ({ data: boardRef, isLoading: isBoardLoadingRef }),
        } as any)

        vi.mocked(usePost).mockReturnValue({
            usePostDetail: mockUsePostDetail,
            useCreatePost: mockUseCreatePost,
            useUpdatePost: mockUseUpdatePost,
            useSaveDraft: () => ({ isPending: isSaveDraftPendingRef, mutateAsync: mockSaveDraftMutateAsync }),
            useDeleteDraft: () => ({ isPending: isDeleteDraftPendingRef, mutateAsync: mockDeleteDraftMutateAsync }),
        } as any)

        mockUsePostDetail.mockImplementation(() => ({ data: postRef, isLoading: isPostLoadingRef }))
        mockUseCreatePost.mockImplementation(() => ({ mutate: mockCreateMutate, isPending: isCreatePendingRef }))
        mockUseUpdatePost.mockImplementation(() => ({ mutate: mockUpdateMutate, isPending: isUpdatePendingRef }))
        mockSaveDraftMutateAsync.mockResolvedValue({
            data: {
                data: {
                    draftId: 91,
                    boardId: 1,
                    boardUrl: 'free',
                    boardName: 'Free',
                    title: 'Draft',
                    contents: 'Draft body',
                    tags: [],
                    fileIds: [],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-01T00:00:00.000Z',
                    modifiedAt: '2025-01-01T00:00:00.000Z',
                },
            },
        })
        mockDeleteDraftMutateAsync.mockResolvedValue({ data: { data: null } })
    })

    it('renders create and edit titles', async () => {
        const createWrapper = mountPostForm('create')
        const editWrapper = mountPostForm('edit')
        await nextTick()

        expect(createWrapper.text()).toContain('board.writePost.createTitle')
        expect(editWrapper.text()).toContain('board.writePost.editTitle')
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

    it('saves a draft from the header action when drafts are enabled', async () => {
        vi.mocked(useAuthStore).mockReturnValue({
            isAuthenticated: true,
            user: { userId: 1, role: 'USER' },
        } as any)
        const wrapper = mountPostForm('create')

        await wrapper.get('#title').setValue('Draft title')
        await wrapper.get('[data-testid="editor-input"]').setValue('Draft body')
        await findButtonByText(wrapper, 'board.writePost.actions.saveDraft').trigger('click')
        await flushPromises()

        expect(mockSaveDraftMutateAsync).toHaveBeenCalled()
        expect(mockAddToast).toHaveBeenCalledWith('board.writePost.draftStatus.saved', 'success')
    })

    it('renders overridden create title when provided', async () => {
        const wrapper = mountPostForm('create', {}, {}, { createTitleOverride: '문의 작성' })
        await nextTick()

        expect(wrapper.text()).toContain('문의 작성')
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

    it('validates required fields before submit', async () => {
        const wrapper = mountPostForm('create')

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

        await wrapper.get('#title').setValue('Created title')
        await wrapper.get('#category').setValue('12')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue(
            'Created body <img src="/api/v1/files/7"><img src="/files/8?download=true">',
        )
        await wrapper.get('[data-testid=\"set-tags\"]').trigger('click')
        await wrapper.get('#nsfw').setValue(true)
        await wrapper.get('#spoiler').setValue(true)

        await wrapper.get('form').trigger('submit')

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

        options.onSuccess({ data: { data: 99 } })
        expect(wrapper.emitted('cancel')).toBeUndefined()
    })

    it('saves the draft before create submit and includes draft id', async () => {
        vi.mocked(useAuthStore).mockReturnValue({
            isAuthenticated: true,
            user: { userId: 1, role: 'USER' },
        } as any)
        setBoardCategories([{ categoryId: 12, name: 'General', minWriteRole: 'USER' }])
        const wrapper = mountPostForm('create')

        await wrapper.get('#title').setValue('Created title')
        await wrapper.get('#category').setValue('12')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue('Created body')

        await wrapper.get('form').trigger('submit')
        await flushPromises()

        const [variables] = mockCreateMutate.mock.calls.at(-1) as [any]
        expect(variables.data.draftId).toBe(91)
    })

    it('normalizes local editor preview images before submit', async () => {
        setBoardCategories([{ categoryId: 12, name: 'General', minWriteRole: 'USER' }])
        const wrapper = mountPostForm('create')

        await wrapper.get('#title').setValue('Created title')
        await wrapper.get('#category').setValue('12')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue(
            '<p><img src="blob:https://noviis.kr/local" data-file-id="157" data-server-src="/api/v1/files/157"></p>',
        )

        await wrapper.get('form').trigger('submit')

        const [variables] = mockCreateMutate.mock.calls.at(-1) as [any]
        expect(variables.data.fileIds).toEqual([157])
        expect(variables.data.contents).toContain('src="/api/v1/files/157"')
        expect(variables.data.contents).not.toContain('blob:https://noviis.kr/local')
        expect(variables.data.contents).not.toContain('data-file-id')
    })

    it('keeps redirectOnCreate as a compatibility prop without navigating internally', async () => {
        setBoardCategories([{ categoryId: 1, name: 'General', minWriteRole: 'USER' }])
        const wrapper = mountPostForm('create', {}, {}, { redirectOnCreate: '/inquiry' })

        await wrapper.get('#title').setValue('Created title')
        await wrapper.get('#category').setValue('1')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue('Created body')
        await wrapper.get('form').trigger('submit')

        const [, options] = mockCreateMutate.mock.calls[0]
        options.onSuccess({ data: { data: 101 } })
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

        await wrapper.get('#title').setValue('Created title')
        await wrapper.get('#category').setValue('1')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue('Created body')
        await wrapper.get('form').trigger('submit')

        const [, options] = mockCreateMutate.mock.calls[0]
        options.onSuccess({ data: { data: 103 } })

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

        await wrapper.get('#title').setValue('Created title')
        await wrapper.get('#category').setValue('1')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue('Created body')
        await wrapper.get('form').trigger('submit')

        const [, options] = mockCreateMutate.mock.calls[0]
        options.onSuccess({ data: { data: 102 } })

        expect(wrapper.emitted('cancel')).toBeUndefined()
    })

    it('logs errors when create or update fails', async () => {
        const wrapperCreate = mountPostForm('create')
        await wrapperCreate.get('#title').setValue('Create error case')
        await wrapperCreate.get('#category').setValue('1')
        await wrapperCreate.get('form').trigger('submit')

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
        await wrapperEdit.get('form').trigger('submit')

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
        await wrapper.get('form').trigger('submit')

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
        await wrapper.get('form').trigger('submit')

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

    it('saves the draft before update submit and includes draft id', async () => {
        vi.mocked(useAuthStore).mockReturnValue({
            isAuthenticated: true,
            user: { userId: 1, role: 'USER' },
        } as any)
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

        const [variables] = mockUpdateMutate.mock.calls.at(-1) as [any]
        expect(variables.data.draftId).toBe(91)
    })

    it('preserves existing edit image file ids from post content', async () => {
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
        await wrapper.get('form').trigger('submit')

        const [variables] = mockUpdateMutate.mock.calls.at(-1) as [any]
        expect(variables.data.fileIds).toEqual([31, 32])
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
        await wrapper.get('form').trigger('submit')

        const [variables] = mockUpdateMutate.mock.calls.at(-1) as [any]
        expect(variables.data.categoryId).toBe(9)
    })

    it('handles video popover and emoticon insertion', async () => {
        const wrapper = mountPostForm('create')

        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')
        expect(wrapper.find('.video-url-popover').exists()).toBe(true)

        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(mockAddToast).toHaveBeenCalledWith('board.writePost.videoUrlRequired', 'error')
        expect(editorSetVideo).not.toHaveBeenCalled()

        await wrapper.get('.video-url-popover-input').setValue('https://example.com/video')
        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(mockAddToast).toHaveBeenLastCalledWith('board.writePost.invalidVideoUrl', 'error')
        expect(editorSetVideo).not.toHaveBeenCalled()

        await wrapper.get('.video-url-popover-input').setValue('https://youtu.be/abc123')
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

        await wrapper.get('.video-url-popover-input').setValue('vimeo.com/12345')
        await wrapper.get('.video-url-popover-actions button:last-child').trigger('click')
        expect(editorSetVideo).toHaveBeenCalledWith('https://player.vimeo.com/video/12345/')
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
        wrapper.get('.video-url-popover-input').element.dispatchEvent(inputEscape)
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
        expect(wrapper.emitted('cancel')).toHaveLength(1)

        const exposed = wrapper.vm as unknown as {
            hasUnsavedChanges: () => boolean
            getLeaveConfirmMessage: () => string
        }
        expect(exposed.hasUnsavedChanges()).toBe(true)
        expect(exposed.getLeaveConfirmMessage()).toBe('board.writePost.leaveConfirm')

        const beforeUnloadEvent = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
        window.dispatchEvent(beforeUnloadEvent)
        expect(beforeUnloadEvent.defaultPrevented).toBe(true)
    })

    it('shows spinner while loading', () => {
        isBoardLoadingRef.value = true
        const wrapper = mountPostForm('create')

        expect(wrapper.text()).toContain('loading')
    })

    it('supports html source mode and emits cancel from the cancel button', async () => {
        boardRef.value = {
            allowNsfw: true,
            isAdmin: true,
            categories: [{ categoryId: 1, name: 'General', minWriteRole: 'USER' }],
        }
        const wrapper = mountPostForm('create')

        const modeButtons = wrapper.findAll('.editor-view-toggle-btn')
        await modeButtons[1].trigger('click')
        expect(wrapper.find('#content').exists()).toBe(true)

        await wrapper.get('#content').setValue('<p>html-source</p>')
        await modeButtons[0].trigger('click')
        expect((wrapper.get('[data-testid=\"editor-input\"]').element as HTMLTextAreaElement).value).toBe('<p>html-source</p>')

        expect(wrapper.find('#isNotice').exists()).toBe(true)
        expect(wrapper.find('#nsfw').exists()).toBe(true)
        expect(wrapper.find('#spoiler').exists()).toBe(true)
        expect(wrapper.find('#secret').exists()).toBe(true)

        const cancelButton = wrapper.findAll('button').find((button) => button.text() === 'common.cancel')
        expect(cancelButton).toBeTruthy()
        await cancelButton?.trigger('click')
        expect(wrapper.emitted('cancel')).toHaveLength(1)
    })

    it('keeps uploaded file ids after switching to html source mode', async () => {
        const UploadingEditorStub = defineComponent({
            name: 'PostEditorTipTap',
            props: {
                modelValue: { type: String, default: '' },
            },
            emits: ['update:modelValue', 'file-uploaded'],
            setup(props, { emit, expose }) {
                expose({
                    setVideo: vi.fn(),
                    setEmoticon: vi.fn(),
                    fileIds: { value: [] },
                })

                return () =>
                    h('div', [
                        h('textarea', {
                            'data-testid': 'editor-input',
                            value: props.modelValue,
                            onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
                        }),
                        h(
                            'button',
                            {
                                type: 'button',
                                'data-testid': 'upload-image',
                                onClick: () => {
                                    emit('file-uploaded', 42)
                                    emit('update:modelValue', '<p><img src="/api/v1/files/42"></p>')
                                },
                            },
                            'upload',
                        ),
                    ])
            },
        })

        const wrapper = mountPostForm('create', { PostEditorTipTap: UploadingEditorStub })
        await wrapper.get('#title').setValue('With image')
        await wrapper.get('#category').setValue('1')
        await wrapper.get('[data-testid="upload-image"]').trigger('click')

        const modeButtons = wrapper.findAll('.editor-view-toggle-btn')
        await modeButtons[1].trigger('click')
        expect(wrapper.find('[data-testid="editor-input"]').exists()).toBe(false)

        await wrapper.get('form').trigger('submit')

        const [variables] = mockCreateMutate.mock.calls.at(-1) as [any]
        expect(variables.data.fileIds).toEqual([42])
    })

    it('covers unsaved-change helper branches for empty snapshot, category and tag changes', async () => {
        setBoardCategories([])
        const noCategoryWrapper = mountPostForm('create')
        const noCategoryExposed = noCategoryWrapper.vm as unknown as { hasUnsavedChanges: () => boolean }
        expect(noCategoryExposed.hasUnsavedChanges()).toBe(false)

        setBoardCategories([
            { categoryId: 1, name: 'General', minWriteRole: 'USER' },
            { categoryId: 2, name: 'Second', minWriteRole: 'USER' },
        ])
        const categoryDirtyWrapper = mountPostForm('create')
        await nextTick()
        await categoryDirtyWrapper.get('#category').setValue('2')
        const categoryDirtyExposed = categoryDirtyWrapper.vm as unknown as { hasUnsavedChanges: () => boolean }
        expect(categoryDirtyExposed.hasUnsavedChanges()).toBe(true)

        const tagsLengthDirtyWrapper = mountPostForm('create')
        await nextTick()
        await tagsLengthDirtyWrapper.get('[data-testid=\"set-tags\"]').trigger('click')
        const tagsLengthDirtyExposed = tagsLengthDirtyWrapper.vm as unknown as { hasUnsavedChanges: () => boolean }
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
        const tagsValueDirtyExposed = tagsValueDirtyWrapper.vm as unknown as { hasUnsavedChanges: () => boolean }
        expect(tagsValueDirtyExposed.hasUnsavedChanges()).toBe(true)
    })

    it('shows submitting labels for create and edit pending states', async () => {
        isCreatePendingRef.value = true
        const createWrapper = mountPostForm('create')
        await nextTick()
        expect(createWrapper.text()).toContain('board.writePost.submitting')

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

        const NoToolbarEditorStub = defineComponent({
            name: 'PostEditorTipTap',
            props: {
                modelValue: { type: String, default: '' },
            },
            emits: ['update:modelValue', 'open-video'],
            setup(props, { emit, expose }) {
                expose({
                    setVideo: (url: string) => editorSetVideo(url),
                    setEmoticon: vi.fn(),
                    fileIds: { value: [] },
                })

                return () =>
                    h('div', [
                        h('textarea', {
                            'data-testid': 'editor-input',
                            value: props.modelValue,
                            onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
                        }),
                        h(
                            'button',
                            {
                                type: 'button',
                                'data-testid': 'open-video',
                                onClick: () => emit('open-video'),
                            },
                            'open-video',
                        ),
                    ])
            },
        })

        const wrapper = mountPostForm('create', { PostEditorTipTap: NoToolbarEditorStub })
        await wrapper.get('[data-testid=\"open-video\"]').trigger('click')

        const popover = wrapper.get('.video-url-popover')
        expect(popover.attributes('style')).toContain('top: 60px')
        expect(popover.attributes('style')).toContain('left: 0px')

        await wrapper.get('.video-url-popover-input').setValue('  https://example.com/video  ')
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

    it('renders the desktop metadata cards for the compose shell', () => {
        const wrapper = mountPostForm('create')

        expect(wrapper.findAll('.nv-compose-side-card').length).toBeGreaterThanOrEqual(2)
        expect(wrapper.find('aside').classes()).toContain('lg:sticky')
    })

    it('connects mobile and desktop tag labels to unique inputs', () => {
        const wrapper = mountPostForm('create')
        const mobileTagInput = wrapper.get('#post-tags-input-mobile')
        const desktopTagInput = wrapper.get('#post-tags-input-desktop')

        expect(wrapper.findAll('#post-tags-input-mobile')).toHaveLength(1)
        expect(wrapper.findAll('#post-tags-input-desktop')).toHaveLength(1)
        expect(wrapper.get('label[for="post-tags-input-mobile"]').attributes('for')).toBe('post-tags-input-mobile')
        expect(wrapper.get('label[for="post-tags-input-desktop"]').attributes('for')).toBe('post-tags-input-desktop')
        expect(mobileTagInput.attributes('id')).toBe('post-tags-input-mobile')
        expect(desktopTagInput.attributes('id')).toBe('post-tags-input-desktop')
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
        await wrapper.get('form').trigger('submit')

        const [variables] = mockUpdateMutate.mock.calls.at(-1) as [any]
        expect(variables.data).not.toHaveProperty('categoryId')
    })

    it('submits with empty fileIds when editor does not expose fileIds ref', async () => {
        const MinimalEditorStub = defineComponent({
            name: 'PostEditorTipTap',
            props: {
                modelValue: { type: String, default: '' },
            },
            emits: ['update:modelValue'],
            setup(props, { emit, expose }) {
                expose({
                    setVideo: vi.fn(),
                    setEmoticon: vi.fn(),
                })

                return () => h('textarea', {
                    'data-testid': 'editor-input',
                    value: props.modelValue,
                    onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
                })
            },
        })

        const wrapper = mountPostForm('create', { PostEditorTipTap: MinimalEditorStub })
        await wrapper.get('#title').setValue('No files')
        await wrapper.get('#category').setValue('1')
        await wrapper.get('[data-testid=\"editor-input\"]').setValue('Body')
        await wrapper.get('form').trigger('submit')

        const [variables] = mockCreateMutate.mock.calls.at(-1) as [any]
        expect(variables.data.fileIds).toEqual([])
    })
})
