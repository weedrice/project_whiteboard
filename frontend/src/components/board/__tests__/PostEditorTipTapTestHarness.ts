import { vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => {
    type EditorPosition = { pos: number }

    const chain = {
        focus: vi.fn(),
        toggleBold: vi.fn(),
        toggleItalic: vi.fn(),
        toggleUnderline: vi.fn(),
        toggleStrike: vi.fn(),
        setFontSize: vi.fn(),
        unsetFontSize: vi.fn(),
        setLineHeight: vi.fn(),
        unsetLineHeight: vi.fn(),
        unsetColor: vi.fn(),
        setColor: vi.fn(),
        setHighlight: vi.fn(),
        setLink: vi.fn(),
        insertContent: vi.fn(),
        extendMarkRange: vi.fn(),
        unsetLink: vi.fn(),
        insertTable: vi.fn(),
        setTextAlign: vi.fn(),
        setTextSelection: vi.fn(),
        updateAttributes: vi.fn(),
        toggleBulletList: vi.fn(),
        toggleOrderedList: vi.fn(),
        toggleBlockquote: vi.fn(),
        setBlockquote: vi.fn(),
        toggleHeading: vi.fn(),
        toggleCodeBlock: vi.fn(),
        setHorizontalRule: vi.fn(),
        setImage: vi.fn(),
        setVideo: vi.fn(),
        run: vi.fn(),
    }
    Object.values(chain).forEach((method) => {
        method.mockImplementation(() => chain)
    })
    chain.run.mockImplementation(() => true)

    const editor = {
        getHTML: vi.fn(() => '<p>editor-html</p>'),
        chain: vi.fn(() => chain),
        commands: {
            setContent: vi.fn(),
        setTextSelection: vi.fn(),
        updateAttributes: vi.fn(),
        },
        getAttributes: vi.fn((name: string): any => {
            if (name === 'textStyle') return { color: '', fontSize: '', lineHeight: '' }
            if (name === 'highlight') return { color: '#fef08a' }
            if (name === 'link') return { href: '' }
            return {}
        }),
        isActive: vi.fn((name: unknown) => name === 'link'),
        state: {
            selection: { from: 1, to: 1 },
            doc: {
                textBetween: vi.fn(() => 'selected text'),
                content: { size: 8 },
            },
        },
        view: {
            dom: document.createElement('div'),
            focus: vi.fn(),
            posAtCoords: vi.fn((): EditorPosition | null => ({ pos: 3 })),
        },
        destroy: vi.fn(),
    }

    const editorRef: { __v_isRef: true; value: typeof editor | null } = {
        __v_isRef: true as const,
        value: editor,
    }
    type EditorOptions = {
        onUpdate?: (payload: { editor: typeof editor }) => void
        editorProps?: {
            handleDOMEvents?: {
                click?: (view: unknown, event: MouseEvent) => boolean
                keydown?: (view: unknown, event: KeyboardEvent) => boolean
            }
            handleClickOn?: (
                view: unknown,
                pos: number,
                node: { type: { name: string }; attrs: Record<string, unknown> },
                nodePos: number,
                event: MouseEvent,
            ) => boolean
        }
    }
    const editorOptions: { value: EditorOptions | null } = { value: null }

    const toastAdd = vi.fn()
    const loggerError = vi.fn()
    const themeStore = { isDark: false }
    const i18nT = vi.fn((key: string) => key)

    const isUploadingImage = { __v_isRef: true as const, value: false }
    const validateImageFile = vi.fn(() => null as null | 'type' | 'size')
    const uploadImage = vi.fn()
    const abortImageUpload = vi.fn()
    const isAbortUploadError = vi.fn(() => false)

    return {
        chain,
        editor,
        editorRef,
        editorOptions,
        toastAdd,
        loggerError,
        themeStore,
        i18nT,
        isUploadingImage,
        validateImageFile,
        uploadImage,
        abortImageUpload,
        isAbortUploadError,
    }
})

vi.mock('@tiptap/vue-3', () => ({
    useEditor: vi.fn((options: NonNullable<typeof mocks.editorOptions.value>) => {
        mocks.editorOptions.value = options
        return mocks.editorRef
    }),
    EditorContent: defineComponent({
        name: 'EditorContent',
        setup() {
            return () => h('div', { class: 'editor-content-stub' })
        },
    }),
}))

vi.mock('@tiptap/starter-kit', () => ({
    default: { configure: vi.fn(() => ({})) },
}))
vi.mock('@tiptap/extension-underline', () => ({ default: {} }))
vi.mock('@tiptap/extension-text-style', () => ({
    TextStyle: {},
    FontSize: { configure: vi.fn(() => ({})) },
    LineHeight: { configure: vi.fn(() => ({})) },
}))
vi.mock('@tiptap/extension-color', () => ({
    Color: { configure: vi.fn(() => ({})) },
}))
vi.mock('@tiptap/extension-highlight', () => ({ default: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-link', () => ({ default: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-image', () => {
    const extension = {
        configure: vi.fn(() => ({})),
        extend: vi.fn(() => extension),
    }
    return { default: extension }
})
vi.mock('@tiptap/extension-text-align', () => ({ default: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-table', () => ({ TableKit: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-horizontal-rule', () => ({ default: {} }))
vi.mock('@/extensions/tiptap-video', () => ({ Video: {} }))

vi.mock('lucide-vue-next', () => {
    const icon = defineComponent({ name: 'TestIcon', setup: () => () => h('i') })
    return {
        Image: icon,
        Link2: icon,
        List: icon,
        ListOrdered: icon,
        SeparatorHorizontal: icon,
        Smile: icon,
        Table2: icon,
        Video: icon,
        TextAlignStart: icon,
        TextAlignCenter: icon,
        TextAlignEnd: icon,
        TextAlignJustify: icon,
    }
})

vi.mock('vue-i18n', () => ({
    useI18n: () => ({ t: mocks.i18nT }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({ addToast: mocks.toastAdd }),
}))

vi.mock('@/stores/theme', () => ({
    useThemeStore: () => mocks.themeStore,
}))

vi.mock('@/composables/useEditorImageUpload', () => ({
    useEditorImageUpload: () => ({
        isUploadingImage: mocks.isUploadingImage,
        validateImageFile: mocks.validateImageFile,
        uploadImage: mocks.uploadImage,
        abortImageUpload: mocks.abortImageUpload,
        isAbortUploadError: mocks.isAbortUploadError,
    }),
}))

vi.mock('@/utils/logger', () => ({
    default: { error: mocks.loggerError },
}))

import PostEditorTipTap from '../PostEditorTipTap.vue'
import PostEditorImageAltPopover from '../editor/PostEditorImageAltPopover.vue'

const BaseButtonStub = defineComponent({
    name: 'BaseButton',
    props: {
        type: { type: String, default: 'button' },
    },
    emits: ['click'],
    setup(props, { emit, slots }) {
        return () =>
            h(
                'button',
                {
                    type: props.type,
                    onClick: () => emit('click'),
                },
                slots.default?.(),
            )
    },
})

const mountEditor = (modelValue = '<p>initial</p>') => {
    return mount(PostEditorTipTap, {
        props: { modelValue },
        global: {
            stubs: {
                Teleport: true,
                BaseButton: BaseButtonStub,
            },
        },
    })
}

const selectors = {
    link: 'button[title="board.writePost.toolbar.link"]',
    image: 'button[title="board.writePost.toolbar.image"]',
    video: 'button[title="board.writePost.toolbar.video"]',
    bulletList: 'button[title="board.writePost.toolbar.bulletList"]',
    orderedList: 'button[title="board.writePost.toolbar.orderedList"]',
    emoticon: 'button[title="board.writePost.toolbar.emoticon"]',
    slashMenu: 'button[title="board.writePost.toolbar.slashMenu"]',
    divider: 'button[title="board.writePost.toolbar.divider"]',
    tableDialog: 'button[title="board.writePost.toolbar.tableDialog"]',
} as const

export const getPostEditorTipTapMocks = () => mocks

export const triggerEditorUpdate = () => {
    const onUpdate = mocks.editorOptions.value?.onUpdate
    if (!onUpdate) {
        throw new Error('Editor onUpdate handler was not registered')
    }
    onUpdate({ editor: mocks.editor })
}

export const getEditorDomEventHandler = <TEvent extends MouseEvent | KeyboardEvent>(
    eventName: keyof NonNullable<
        NonNullable<NonNullable<typeof mocks.editorOptions.value>['editorProps']>['handleDOMEvents']
    >,
) => {
    const handler = mocks.editorOptions.value?.editorProps?.handleDOMEvents?.[eventName]
    if (!handler) {
        throw new Error(`Editor DOM event handler was not registered: ${String(eventName)}`)
    }
    return handler as (view: unknown, event: TEvent) => boolean
}

export const getEditorHandleClickOn = () => {
    const handler = mocks.editorOptions.value?.editorProps?.handleClickOn
    if (!handler) {
        throw new Error('Editor handleClickOn handler was not registered')
    }
    return handler
}

export const setEditorSelection = (selection: unknown) => {
    mocks.editor.state.selection = selection as typeof mocks.editor.state.selection
}

export const mockNextEditorPositionAtCoords = (position: { pos: number } | null) => {
    mocks.editor.view.posAtCoords.mockReturnValueOnce(position)
}

export const setEditorRefValue = (editor: typeof mocks.editor | null) => {
    mocks.editorRef.value = editor
}

export const cleanupPostEditorTipTapTestState = () => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
}

export const resetPostEditorTipTapTestState = () => {
    vi.clearAllMocks()
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:https://noviis.kr/local-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    setEditorRefValue(mocks.editor)
    mocks.themeStore.isDark = false
    mocks.editor.getHTML.mockReturnValue('<p>editor-html</p>')
    mocks.editor.state.selection = { from: 1, to: 1 }
    mocks.editor.state.doc.content.size = 8
    mocks.editor.view.posAtCoords.mockReturnValue({ pos: 3 })
    mocks.validateImageFile.mockReturnValue(null)
    mocks.uploadImage.mockResolvedValue({ url: 'https://cdn.test/image.png', fileId: 55 })
    mocks.isAbortUploadError.mockReturnValue(false)
    mocks.isUploadingImage.value = false
    mocks.i18nT.mockImplementation((key: string) => key)
}

export { mountEditor, PostEditorImageAltPopover, selectors }
