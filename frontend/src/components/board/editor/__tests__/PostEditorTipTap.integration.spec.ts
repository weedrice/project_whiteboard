import { afterEach, describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import {
    decodeSandboxedPostHtml,
    encodeSandboxedPostHtml,
    requiresPreservedPostHtml,
} from '@/utils/postHtmlSandbox'

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

    it('keeps the preservation classifier aligned with the actual editor serializer', () => {
        editor = createEditor([
            '<h2 style="text-align: center">Heading</h2>',
            '<p><strong>Bold</strong> <em>Italic</em> <s>Strike</s> <u>Underline</u></p>',
            '<blockquote><p>Quote</p></blockquote>',
            '<ol start="3"><li><p>Third</p></li></ol>',
            '<pre><code class="language-typescript">const value = 1</code></pre>',
            '<p><a href="https://noviis.kr">Link</a></p>',
            '<p><span style="color: #ff0000; font-size: 18px; line-height: 1.5">Styled</span></p>',
            '<p><mark data-color="#fff000" style="background-color: #fff000; color: #111111">Marked</mark></p>',
            '<p><span class="mention-node" data-type="mention" data-id="7" data-label="Novi" data-mention-user-id="7">@Novi</span></p>',
            '<p><img src="/api/v1/files/42" alt="Diagram" data-file-id="42" data-server-src="/files/42"></p>',
            '<table style="min-width: 75px"><colgroup><col style="min-width: 25px; width: 25px"></colgroup><tbody><tr><th style="text-align: center"><p>Header</p></th><td><p>Cell</p></td></tr></tbody></table>',
            '<div class="tiptap-video-wrapper" data-video-embed="true"><iframe src="https://www.youtube.com/embed/test-id" frameborder="0" allowfullscreen="true"></iframe></div>',
            '<hr>',
        ].join(''))

        const canonicalHtml = editor.getHTML()

        expect(requiresPreservedPostHtml(canonicalHtml)).toBe(false)
        editor.commands.setContent(canonicalHtml, { emitUpdate: false })
        const stabilizedHtml = editor.getHTML()
        expect(stabilizedHtml.replace(/<p><\/p>$/, '')).toBe(canonicalHtml)
        expect(requiresPreservedPostHtml(stabilizedHtml)).toBe(false)
        editor.commands.setContent(stabilizedHtml, { emitUpdate: false })
        expect(editor.getHTML()).toBe(stabilizedHtml)
        const mention = parseHTML(stabilizedHtml).querySelector('[data-type="mention"]')
        expect(mention?.getAttribute('data-id')).toBe('7')
        expect(mention?.getAttribute('data-label')).toBe('Novi')
        expect(mention?.getAttribute('data-mention-suggestion-char')).toBe('@')
        expect(mention?.getAttribute('data-mention-user-id')).toBe('7')
        expect(mention?.textContent).toBe('@Novi')
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
