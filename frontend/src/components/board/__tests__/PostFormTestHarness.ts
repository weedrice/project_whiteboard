import { vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { EmoticonImage } from '@/types/emoticon'
import PostForm from '../PostForm.vue'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/features/board/posts/queries/usePost'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { User } from '@/types'
import logger from '@/utils/logger'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/composables/useBoard', () => ({
    useBoard: vi.fn(),
}))

vi.mock('@/features/board/posts/queries/usePost', () => ({
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
        label: { type: String, default: '' },
        modelValue: { type: [String, Number], default: '' },
    },
    emits: ['update:modelValue'],
    setup(props, { emit, slots }) {
        return () =>
            h('div', [
                props.label ? h('label', { for: props.id }, props.label) : null,
                h(
                    'select',
                    {
                        id: props.id,
                        value: props.modelValue as string | number,
                        onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
                    },
                    slots.default?.(),
                ),
            ])
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
    emits: ['update:modelValue', 'open-video', 'open-emoticon', 'open-poll', 'file-uploaded'],
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
                h(
                    'button',
                    {
                        type: 'button',
                        'data-testid': 'open-poll',
                        onClick: () => emit('open-poll'),
                    },
                    'open-poll',
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
const mockCreateScheduledMutate = vi.fn()
const mockUpdateMutate = vi.fn()
const mockUsePostDetail = vi.fn()
const mockUseCreatePost = vi.fn()
const mockUseCreateScheduledPost = vi.fn()
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
type TestUserOverrides = Partial<User>
type PostFormMutationData = {
    draftId?: number
    fileIds?: number[]
    categoryId?: number
    [key: string]: unknown
}

type CreatePostMutationVariables = {
    boardUrl: string
    data: PostFormMutationData
}

type UpdatePostMutationVariables = {
    postId: string
    data: PostFormMutationData
}
type MountedPostForm = ReturnType<typeof mount>
type FillPostFormOptions = {
    title?: string
    categoryId?: string
    contents?: string
    setTags?: boolean
    nsfw?: boolean
    spoiler?: boolean
}

const boardRef = ref<TestBoard | null>({ allowNsfw: false, isAdmin: false, categories: [] })
const postRef = ref<unknown>(null)
const isBoardLoadingRef = ref(false)
const isPostLoadingRef = ref(false)
const isCreatePendingRef = ref(false)
const isCreateScheduledPendingRef = ref(false)
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

const findEditorModeButtons = (wrapper: ReturnType<typeof mount>) => ({
    visual: findButtonByText(wrapper, 'board.writePost.visualMode'),
    html: findButtonByText(wrapper, 'board.writePost.viewHtmlSource'),
})

const createTestUser = (overrides: TestUserOverrides = {}): User => ({
    userId: 1,
    loginId: 'test',
    displayName: 'Test User',
    email: 'test@example.com',
    role: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-05-27T00:00:00Z',
    ...overrides,
})

const mockPostFormAuthStore = (
    overrides: { isAuthenticated?: boolean; user?: TestUserOverrides | null } = {},
) => {
    vi.mocked(useAuthStore).mockReturnValue({
        isAuthenticated: overrides.isAuthenticated ?? false,
        user: overrides.user === null ? null : createTestUser(overrides.user),
    } as ReturnType<typeof useAuthStore>)
}

const getLastCreatePostVariables = (): CreatePostMutationVariables => {
    const call = mockCreateMutate.mock.calls.at(-1)
    if (!call) {
        throw new Error('Expected create post mutation to be called')
    }
    return call[0] as CreatePostMutationVariables
}

const getLastUpdatePostVariables = (): UpdatePostMutationVariables => {
    const call = mockUpdateMutate.mock.calls.at(-1)
    if (!call) {
        throw new Error('Expected update post mutation to be called')
    }
    return call[0] as UpdatePostMutationVariables
}

const fillPostForm = async (
    wrapper: MountedPostForm,
    {
        title = 'Created title',
        categoryId = '1',
        contents = 'Created body',
        setTags = false,
        nsfw = false,
        spoiler = false,
    }: FillPostFormOptions = {},
) => {
    await wrapper.get('#title').setValue(title)
    if (categoryId) {
        await wrapper.get('#category').setValue(categoryId)
    }
    await wrapper.get('[data-testid="editor-input"]').setValue(contents)
    if (setTags) {
        await wrapper.get('[data-testid="set-tags"]').trigger('click')
    }
    if (nsfw) {
        await wrapper.get('#nsfw').setValue(true)
    }
    if (spoiler) {
        await wrapper.get('#spoiler').setValue(true)
    }
}

const submitPostForm = async (wrapper: MountedPostForm) => {
    await wrapper.get('form').trigger('submit')
}

const createNoToolbarEditorStub = () => defineComponent({
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

const createMinimalEditorStub = () => defineComponent({
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

export const unmountPostFormWrappers = () => {
    while (mountedWrappers.length > 0) {
        mountedWrappers.pop()?.unmount()
    }
}

export const resetPostFormTestState = () => {
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
    isCreateScheduledPendingRef.value = false
    isUpdatePendingRef.value = false

    mockPostFormAuthStore({ user: { role: 'USER' } })
    vi.mocked(useToastStore).mockReturnValue({ addToast: mockAddToast } as unknown as ReturnType<typeof useToastStore>)

    vi.mocked(useBoard).mockReturnValue({
        useBoardDetail: () => ({ data: boardRef, isLoading: isBoardLoadingRef }),
    } as unknown as ReturnType<typeof useBoard>)

    vi.mocked(usePost).mockReturnValue({
        usePostDetail: mockUsePostDetail,
        useCreatePost: mockUseCreatePost,
        useCreateScheduledPost: mockUseCreateScheduledPost,
        useUpdatePost: mockUseUpdatePost,
        useSaveDraft: () => ({ isPending: isSaveDraftPendingRef, mutateAsync: mockSaveDraftMutateAsync }),
        useDeleteDraft: () => ({ isPending: isDeleteDraftPendingRef, mutateAsync: mockDeleteDraftMutateAsync }),
    } as unknown as ReturnType<typeof usePost>)

    mockUsePostDetail.mockImplementation(() => ({ data: postRef, isLoading: isPostLoadingRef }))
    mockUseCreatePost.mockImplementation(() => ({ mutate: mockCreateMutate, isPending: isCreatePendingRef }))
    mockUseCreateScheduledPost.mockImplementation(() => ({ mutate: mockCreateScheduledMutate, isPending: isCreateScheduledPendingRef }))
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
}

export {
    boardRef,
    editorSetEmoticon,
    editorSetVideo,
    fillPostForm,
    findButtonByText,
    findEditorModeButtons,
    createMinimalEditorStub,
    createNoToolbarEditorStub,
    getLastCreatePostVariables,
    getLastUpdatePostVariables,
    isBoardLoadingRef,
    isCreatePendingRef,
    isCreateScheduledPendingRef,
    isUpdatePendingRef,
    mockAddToast,
    mockCreateScheduledMutate,
    mockCreateMutate,
    mockSaveDraftMutateAsync,
    mockUpdateMutate,
    mockUsePostDetail,
    mockPostFormAuthStore,
    mountPostForm,
    postRef,
    routeState,
    setBoardCategories,
    submitPostForm,
}
