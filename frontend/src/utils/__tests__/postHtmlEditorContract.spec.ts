import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { Editor } from '@tiptap/core'
import { afterEach, describe, expect, it } from 'vitest'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { requiresPreservedPostHtml } from '../postHtmlSandbox'

type ContractCase = {
    name: string
    selector: string
    html: string
    attributes: Record<string, string>
}

type PostHtmlEditorContract = {
    version: number
    cases: ContractCase[]
}

const contract = JSON.parse(readFileSync(
    resolve(process.cwd(), '../docs/contracts/post-html-editor.json'),
    'utf8',
)) as PostHtmlEditorContract

describe('post HTML server/editor contract', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    it('uses the supported contract version', () => {
        expect(contract.version).toBe(1)
    })

    it.each(contract.cases)('$name remains editable and stable in TipTap', ({ html, selector, attributes }) => {
        expect(requiresPreservedPostHtml(html)).toBe(false)

        editor = new Editor({
            content: html,
            editable: true,
            extensions: createPostEditorExtensions(),
        })
        const serialized = editor.getHTML()
        const document = new DOMParser().parseFromString(serialized, 'text/html')
        const element = document.querySelector(selector)

        expect(element).not.toBeNull()
        Object.entries(attributes).forEach(([name, value]) => {
            expect(normalizeAttributeValue(name, element?.getAttribute(name)), `${selector}[${name}]`)
                .toBe(normalizeAttributeValue(name, value))
        })
        expect(requiresPreservedPostHtml(serialized)).toBe(false)

        editor.commands.setContent(serialized, { emitUpdate: false })
        const stabilized = editor.getHTML()
        expect(requiresPreservedPostHtml(stabilized)).toBe(false)
        editor.commands.setContent(stabilized, { emitUpdate: false })
        expect(editor.getHTML()).toBe(stabilized)
    })
})

function normalizeAttributeValue(name: string, value: string | null | undefined): string {
    if (name !== 'style') return value ?? ''
    return (value ?? '')
        .split(';')
        .map((declaration) => declaration.trim())
        .filter(Boolean)
        .join('; ')
}
