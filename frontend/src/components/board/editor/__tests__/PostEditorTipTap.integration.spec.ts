import { afterEach, describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import {
    ensurePostEditorEditableTail,
    serializePostEditorHtml,
} from '@/features/board/posts/editor/usePostEditorInstance'
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
        expect(anchor?.getAttribute('rel')).toBe('nofollow noopener noreferrer')
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

    it('round-trips a youtube-nocookie video without falling back to raw HTML', () => {
        const source = '<iframe src="https://www.youtube-nocookie.com/embed/private-id"></iframe>'

        expect(requiresPreservedPostHtml(source)).toBe(false)
        editor = createEditor(source)

        const serialized = editor.getHTML()
        expect(parseHTML(serialized).querySelector('iframe')?.getAttribute('src'))
            .toBe('https://www.youtube-nocookie.com/embed/private-id')
        expect(requiresPreservedPostHtml(serialized)).toBe(false)
    })

    it('round-trips a preserved raw HTML block without changing its source', () => {
        const rawHtml = '<!doctype html><style>.card{display:grid}</style><button onclick="run()">실행</button><script>run()</script>'
        editor = createEditor(encodeSandboxedPostHtml(rawHtml))

        const serialized = editor.getHTML()
        const marker = parseHTML(serialized).querySelector('.noviis-sandboxed-post-html')

        expect(marker).not.toBeNull()
        expect(decodeSandboxedPostHtml(serialized)).toBe(rawHtml)
    })

    it('treats preserved HTML as a draggable atom and creates an editable paragraph with Enter', () => {
        const rawHtml = '<style>.card{display:grid}</style><section class="card">보존할 내용</section>'
        editor = createEditor(encodeSandboxedPostHtml(rawHtml))

        expect(editor.schema.nodes.rawHtmlBlock.spec.atom).toBe(true)
        expect(editor.schema.nodes.rawHtmlBlock.spec.draggable).toBe(true)
        expect(editor.commands.setNodeSelection(0)).toBe(true)
        expect(editor.state.selection.toJSON()).toMatchObject({ type: 'node', anchor: 0 })
        const enterEvent = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
        editor.view.dom.dispatchEvent(enterEvent)
        expect(enterEvent.defaultPrevented).toBe(true)
        expect(editor.getHTML()).toContain('noviis-sandboxed-post-html')
        expect(editor.state.selection.toJSON()).toMatchObject({ type: 'text' })
        expect(editor.commands.insertContent('일반 문단')).toBe(true)

        const serialized = editor.getHTML()
        const document = parseHTML(serialized)
        const marker = document.querySelector('.noviis-sandboxed-post-html')

        expect(marker?.nextElementSibling?.outerHTML).toBe('<p>일반 문단</p>')
        expect(decodeSandboxedPostHtml(marker?.outerHTML ?? '')).toBe(rawHtml)
    })

    it('always keeps an editable paragraph after a trailing preserved HTML block', () => {
        const rawHtml = '<style>.card{display:grid}</style><section class="card">보존할 내용</section>'
        const preserved = encodeSandboxedPostHtml(rawHtml)
        editor = createEditor(ensurePostEditorEditableTail(preserved))

        expect(editor.state.doc.lastChild?.type.name).toBe('paragraph')
        const initialDocument = parseHTML(editor.getHTML())
        expect(initialDocument.body.lastElementChild?.outerHTML).toBe('<p></p>')
        expect(decodeSandboxedPostHtml(initialDocument.querySelector('.noviis-sandboxed-post-html')?.outerHTML ?? '')).toBe(rawHtml)

        const paragraphSize = editor.state.doc.lastChild?.nodeSize ?? 0
        const paragraphStart = editor.state.doc.content.size - paragraphSize
        expect(editor.commands.deleteRange({ from: paragraphStart, to: editor.state.doc.content.size })).toBe(true)
        expect(editor.state.doc.lastChild?.type.name).toBe('paragraph')

        const marker = parseHTML(editor.getHTML()).querySelector('.noviis-sandboxed-post-html')
        expect(decodeSandboxedPostHtml(marker?.outerHTML ?? '')).toBe(rawHtml)
    })

    it.each([
        ['ArrowRight', 'after'],
        ['ArrowDown', 'after'],
        ['ArrowLeft', 'before'],
        ['ArrowUp', 'before'],
    ])('moves from a selected HTML block to an editable paragraph with %s', (key, expectedSide) => {
        const rawHtml = '<style>.card{display:grid}</style><section>블록</section>'
        const preserved = encodeSandboxedPostHtml(rawHtml)
        editor = createEditor(preserved)
        expect(editor.commands.setNodeSelection(0)).toBe(true)

        const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
        editor.view.dom.dispatchEvent(event)

        expect(event.defaultPrevented).toBe(true)
        expect(editor.state.selection.toJSON()).toMatchObject({ type: 'text' })
        const document = parseHTML(editor.getHTML())
        const marker = document.querySelector('.noviis-sandboxed-post-html')
        const paragraph = expectedSide === 'after'
            ? marker?.nextElementSibling
            : marker?.previousElementSibling
        expect(paragraph?.outerHTML).toBe('<p></p>')
        expect(decodeSandboxedPostHtml(marker?.outerHTML ?? '')).toBe(rawHtml)
    })

    it('creates a cursor stop between consecutive preserved HTML blocks', () => {
        const firstRawHtml = '<style>.first{display:grid}</style><section>첫 번째</section>'
        const secondRawHtml = '<style>.second{display:flex}</style><section>두 번째</section>'
        editor = createEditor(`${encodeSandboxedPostHtml(firstRawHtml)}${encodeSandboxedPostHtml(secondRawHtml)}`)
        expect(editor.commands.setNodeSelection(0)).toBe(true)

        editor.view.dom.dispatchEvent(new KeyboardEvent('keydown', {
            key: 'ArrowRight',
            bubbles: true,
            cancelable: true,
        }))

        const document = parseHTML(editor.getHTML())
        expect(Array.from(document.body.children).map((element) => element.tagName)).toEqual(['DIV', 'P', 'DIV', 'P'])
        expect(editor.state.selection.toJSON()).toMatchObject({ type: 'text' })
    })

    it('requires explicit block selection before deletion and restores the exact payload with undo', () => {
        const rawHtml = '<style>.card{display:grid}</style><section>삭제 복원</section>'
        const preserved = encodeSandboxedPostHtml(rawHtml)
        editor = createEditor(ensurePostEditorEditableTail(preserved))

        expect(editor.commands.setTextSelection(2)).toBe(true)
        editor.view.dom.dispatchEvent(new KeyboardEvent('keydown', {
            key: 'Backspace',
            bubbles: true,
            cancelable: true,
        }))
        expect(editor.state.selection.toJSON()).toMatchObject({ type: 'node', anchor: 0 })
        expect(editor.getHTML()).toContain('noviis-sandboxed-post-html')

        editor.view.dom.dispatchEvent(new KeyboardEvent('keydown', {
            key: 'Backspace',
            bubbles: true,
            cancelable: true,
        }))
        expect(editor.getHTML()).not.toContain('noviis-sandboxed-post-html')
        expect(editor.commands.undo()).toBe(true)

        const marker = parseHTML(editor.getHTML()).querySelector('.noviis-sandboxed-post-html')
        expect(decodeSandboxedPostHtml(marker?.outerHTML ?? '')).toBe(rawHtml)
    })

    it('removes only the editor-owned empty tail from serialized preserved HTML', () => {
        const rawHtml = '<style>.card{display:grid}</style><section class="card">원문</section>'
        const preserved = encodeSandboxedPostHtml(rawHtml)

        expect(serializePostEditorHtml(`${preserved}<p></p>`)).toBe(preserved)
        expect(serializePostEditorHtml(`${preserved}<p>사용자 본문</p>`)).toBe(`${preserved}<p>사용자 본문</p>`)
        expect(serializePostEditorHtml('<p>일반 본문</p><p></p>')).toBe('<p>일반 본문</p><p></p>')
        expect(serializePostEditorHtml(`<p></p>${preserved}<p></p>`)).toBe(`<p></p>${preserved}`)
    })

    it('copies and pastes a preserved HTML atom without changing its payload', () => {
        const rawHtml = '<style>.copy{display:grid}</style><section onclick="run()">복사</section><script>run()</script>'
        editor = createEditor(ensurePostEditorEditableTail(encodeSandboxedPostHtml(rawHtml)))
        expect(editor.commands.setNodeSelection(0)).toBe(true)

        const copiedSlice = editor.state.selection.content()
        expect(copiedSlice.content.firstChild?.attrs.html).toBe(rawHtml)
        expect(editor.chain()
            .setTextSelection(editor.state.doc.content.size - 1)
            .insertContent(copiedSlice.content.toJSON())
            .run()).toBe(true)

        const markers = Array.from(parseHTML(editor.getHTML()).querySelectorAll('.noviis-sandboxed-post-html'))
        expect(markers).toHaveLength(2)
        markers.forEach((marker) => {
            expect(decodeSandboxedPostHtml(marker.outerHTML)).toBe(rawHtml)
        })
    })

    it('keeps the preserved payload intact when an HTML block is moved', () => {
        const rawHtml = '<style>.move{display:flex}</style><section>이동</section>'
        editor = createEditor(`<p>앞</p>${encodeSandboxedPostHtml(rawHtml)}<p>뒤</p>`)
        let rawBlockPosition = -1
        editor.state.doc.descendants((node, position) => {
            if (node.type.name === 'rawHtmlBlock') rawBlockPosition = position
        })
        const rawBlock = editor.state.doc.nodeAt(rawBlockPosition)
        expect(rawBlock).not.toBeNull()

        let transaction = editor.state.tr.delete(rawBlockPosition, rawBlockPosition + (rawBlock?.nodeSize ?? 0))
        transaction = transaction.insert(transaction.doc.content.size, rawBlock!)
        editor.view.dispatch(transaction)

        const document = parseHTML(editor.getHTML())
        const markers = document.querySelectorAll('.noviis-sandboxed-post-html')
        expect(markers).toHaveLength(1)
        expect(decodeSandboxedPostHtml(markers[0].outerHTML)).toBe(rawHtml)
        expect(document.body.lastElementChild?.outerHTML).toBe('<p></p>')
    })

    it('recovers an invalid preserved marker as safely encoded source instead of dropping it', () => {
        const damagedMarker = '<div class="noviis-sandboxed-post-html" data-value="%%%invalid%%%" onclick="evil()"></div>'
        editor = createEditor(damagedMarker)

        const serializedMarker = parseHTML(editor.getHTML()).querySelector('.noviis-sandboxed-post-html')
        const recoveredSource = decodeSandboxedPostHtml(serializedMarker?.outerHTML ?? '')
        expect(recoveredSource).toContain('data-value="%%%invalid%%%"')
        expect(recoveredSource).toContain('onclick="evil()"')
        expect(serializedMarker?.hasAttribute('onclick')).toBe(false)
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
