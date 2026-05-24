import { afterEach, describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import { TextStyle, FontSize, LineHeight } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import Highlight from '@tiptap/extension-highlight'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import TextAlign from '@tiptap/extension-text-align'
import { TableKit } from '@tiptap/extension-table'
import HorizontalRule from '@tiptap/extension-horizontal-rule'
import { Video } from '@/extensions/tiptap-video'

const EditorImage = Image.extend({
    addAttributes() {
        return {
            ...this.parent?.(),
            fileId: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute('data-file-id'),
                renderHTML: (attributes: { fileId?: string | number | null }) => (
                    attributes.fileId ? { 'data-file-id': String(attributes.fileId) } : {}
                ),
            },
            serverSrc: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute('data-server-src'),
                renderHTML: (attributes: { serverSrc?: string | null }) => (
                    attributes.serverSrc ? { 'data-server-src': attributes.serverSrc } : {}
                ),
            },
        }
    },
})

const createEditor = (content = '') => new Editor({
    content,
    editable: true,
    extensions: [
        StarterKit.configure({
            heading: { levels: [1, 2, 3, 4, 5, 6] },
            horizontalRule: false,
            link: false,
            underline: false,
        }),
        Underline,
        TextStyle,
        Color.configure({ types: ['textStyle'] }),
        Highlight.configure({ multicolor: true }),
        FontSize.configure({ types: ['textStyle'] }),
        LineHeight.configure({ types: ['textStyle'] }),
        Link.configure({
            openOnClick: false,
            HTMLAttributes: {
                rel: 'noopener noreferrer',
                target: '_blank',
                class: 'tiptap-link',
            },
        }),
        EditorImage.configure({
            inline: true,
            allowBase64: false,
            HTMLAttributes: { class: 'tiptap-image-inline max-w-full h-auto align-baseline' },
        }),
        TextAlign.configure({
            types: ['heading', 'paragraph', 'tableCell', 'tableHeader'],
        }),
        TableKit.configure({
            table: {
                resizable: true,
                handleWidth: 6,
                cellMinWidth: 40,
            },
        }),
        HorizontalRule,
        Video,
    ],
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
