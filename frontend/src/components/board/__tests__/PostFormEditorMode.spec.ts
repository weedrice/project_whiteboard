import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import {
    boardRef,
    findEditorModeButtons,
    getLastCreatePostVariables,
    mountPostForm,
    resetPostFormTestState,
    unmountPostFormWrappers,
} from './PostFormTestHarness'

describe('PostForm editor mode', () => {
    afterEach(() => {
        unmountPostFormWrappers()
    })

    beforeEach(() => {
        resetPostFormTestState()
    })
    it('supports html source mode and emits cancel from the cancel button', async () => {
        boardRef.value = {
            allowNsfw: true,
            isAdmin: true,
            categories: [{ categoryId: 1, name: 'General', minWriteRole: 'USER' }],
        }
        const wrapper = mountPostForm('create')

        const modeButtons = findEditorModeButtons(wrapper)
        expect(modeButtons.visual.attributes('aria-pressed')).toBe('true')
        expect(modeButtons.html.attributes('aria-pressed')).toBe('false')

        await modeButtons.html.trigger('click')
        expect(wrapper.find('#content').exists()).toBe(true)
        expect(modeButtons.visual.attributes('aria-pressed')).toBe('false')
        expect(modeButtons.html.attributes('aria-pressed')).toBe('true')

        await wrapper.get('#content').setValue('<p>html-source</p>')
        await modeButtons.visual.trigger('click')
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

    it('normalizes uploaded image preview sources when switching html source mode', async () => {
        const wrapper = mountPostForm('create')
        const uploadedImageContent = '<p><img src="blob:https://noviis.kr/local" data-file-id="157" data-server-src="/files/157"></p>'

        await wrapper.get('[data-testid="editor-input"]').setValue(uploadedImageContent)

        const modeButtons = findEditorModeButtons(wrapper)
        await modeButtons.html.trigger('click')

        const htmlSource = wrapper.get('#content').element as HTMLTextAreaElement
        expect(htmlSource.value).toContain('src="/api/v1/files/157"')
        expect(htmlSource.value).toContain('data-file-id="157"')
        expect(htmlSource.value).toContain('data-server-src="/files/157"')
        expect(htmlSource.value).not.toContain('blob:https://noviis.kr/local')

        await modeButtons.visual.trigger('click')
        expect((wrapper.get('[data-testid="editor-input"]').element as HTMLTextAreaElement).value).toContain('src="/api/v1/files/157"')
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

        const modeButtons = findEditorModeButtons(wrapper)
        await modeButtons.html.trigger('click')
        expect(wrapper.find('[data-testid="editor-input"]').exists()).toBe(false)

        await wrapper.get('form').trigger('submit')

        const variables = getLastCreatePostVariables()
        expect(variables.data.fileIds).toEqual([42])
    })

    it('keeps script-style html widgets in html source mode', async () => {
        const wrapper = mountPostForm('create')
        const modeButtons = findEditorModeButtons(wrapper)
        const rawWidget = '<style>.cl{display:grid}</style><button onclick="toggle()">여권</button><script>function toggle(){}</script>'

        await modeButtons.html.trigger('click')
        await wrapper.get('#content').setValue(rawWidget)
        await modeButtons.visual.trigger('click')

        expect(wrapper.find('#content').exists()).toBe(true)
        expect((wrapper.get('#content').element as HTMLTextAreaElement).value).toBe(rawWidget)
        expect(findEditorModeButtons(wrapper).html.attributes('aria-pressed')).toBe('true')
    })

})
