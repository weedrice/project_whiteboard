import { afterEach, describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { decodeSandboxedPostHtml, encodeSandboxedPostHtml } from '@/utils/postHtmlSandbox'

const createEditor = (content = '') => new Editor({
    content,
    editable: true,
    extensions: createPostEditorExtensions(),
})

const parseHTML = (html: string) => new DOMParser().parseFromString(html, 'text/html')

describe('PostEditorTipTap TipTap extension integration', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    it('registers each extension name only once', () => {
        editor = createEditor()
        const names = editor.extensionManager.extensions.map((extension) => extension.name)

        expect(names).toHaveLength(new Set(names).size)
    })

    it('serializes link marks with the configured anchor attributes', () => {
        editor = createEditor()
        editor.commands.setContent({
            type: 'doc',
            content: [
                {
                    type: 'paragraph',
                    content: [
                        {
                            type: 'text',
                            text: 'NoviIs',
                            marks: [
                                {
                                    type: 'link',
                                    attrs: { href: 'https://noviis.kr/posts/1' },
                                },
                            ],
                        },
                    ],
                },
            ],
        })

        const anchor = parseHTML(editor.getHTML()).querySelector('a')

        expect(anchor?.textContent).toBe('NoviIs')
        expect(anchor?.getAttribute('href')).toBe('https://noviis.kr/posts/1')
        expect(anchor?.getAttribute('class')).toBe('tiptap-link')
        expect(anchor?.getAttribute('target')).toBe('_blank')
        expect(anchor?.getAttribute('rel')).toBe('noopener noreferrer')
    })

    it('serializes image src, data-file-id and alt attributes', () => {
        editor = createEditor()
        editor.commands.setContent({
            type: 'doc',
            content: [
                {
                    type: 'paragraph',
                    content: [
                        {
                            type: 'image',
                            attrs: {
                                src: 'https://cdn.noviis.kr/uploads/editor.png',
                                fileId: 42,
                                alt: 'Uploaded diagram',
                            },
                        },
                    ],
                },
            ],
        })

        const image = parseHTML(editor.getHTML()).querySelector('img')

        expect(image?.getAttribute('src')).toBe('https://cdn.noviis.kr/uploads/editor.png')
        expect(image?.getAttribute('data-file-id')).toBe('42')
        expect(image?.getAttribute('alt')).toBe('Uploaded diagram')
        expect(image?.className).toContain('tiptap-image-inline')
    })

    it('serializes inserted table HTML with rows, columns and header cells', () => {
        editor = createEditor()
        editor.commands.insertTable({ rows: 2, cols: 3, withHeaderRow: true })

        const document = parseHTML(editor.getHTML())
        const table = document.querySelector('table')
        const rows = Array.from(document.querySelectorAll('tr'))

        expect(table).not.toBeNull()
        expect(rows).toHaveLength(2)
        expect(rows[0].querySelectorAll('th')).toHaveLength(3)
        expect(rows[1].querySelectorAll('td')).toHaveLength(3)
    })

    it('serializes video HTML through the custom TipTap command', () => {
        editor = createEditor()
        expect(editor.commands.setVideo({ src: 'https://www.youtube.com/embed/test-id' })).toBe(true)

        const document = parseHTML(editor.getHTML())
        const wrapper = document.querySelector('.tiptap-video-wrapper')
        const iframe = wrapper?.querySelector('iframe')

        expect(wrapper?.hasAttribute('data-video-embed')).toBe(true)
        expect(iframe?.getAttribute('src')).toBe('https://www.youtube.com/embed/test-id')
        expect(iframe?.getAttribute('allowfullscreen')).toBe('true')
    })

    it('round-trips a preserved raw HTML block without changing its source', () => {
        const rawHtml = '<!doctype html><style>.card{display:grid}</style><button onclick="run()">실행</button><script>run()</script>'
        editor = createEditor(encodeSandboxedPostHtml(rawHtml))

        const serialized = editor.getHTML()
        const marker = parseHTML(serialized).querySelector('.noviis-sandboxed-post-html')

        expect(marker).not.toBeNull()
        expect(decodeSandboxedPostHtml(serialized)).toBe(rawHtml)
    })

    it('inserts slash-menu equivalent block commands through the same editor command path', () => {
        editor = createEditor('<p>Quote target</p>')
        expect(editor.chain().focus().setBlockquote().run()).toBe(true)
        expect(parseHTML(editor.getHTML()).querySelector('blockquote p')?.textContent).toBe('Quote target')

        editor.commands.setContent('<p>List target</p>')
        expect(editor.chain().focus().toggleBulletList().run()).toBe(true)
        expect(parseHTML(editor.getHTML()).querySelector('ul li p')?.textContent).toBe('List target')

        editor.commands.setContent('')
        expect(editor.chain().focus().setHorizontalRule().run()).toBe(true)
        expect(parseHTML(editor.getHTML()).querySelector('hr')).not.toBeNull()
    })
})
