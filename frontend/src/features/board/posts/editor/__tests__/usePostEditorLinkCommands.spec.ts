import { afterEach, describe, expect, it, vi } from 'vitest'
import { Editor } from '@tiptap/core'
import { ref, shallowRef } from 'vue'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { usePostEditorLinkCommands } from '../usePostEditorLinkCommands'

const editors: Editor[] = []

function createCommands(content: string, selection: number | { from: number; to: number }) {
  const editor = new Editor({ content, extensions: createPostEditorExtensions() })
  editors.push(editor)
  editor.commands.setTextSelection(selection)
  const showLinkPopover = ref(false)
  const addToast = vi.fn()
  const commands = usePostEditorLinkCommands({
    editor: shallowRef(editor),
    showLinkPopover,
    linkPosition: { setAnchor: vi.fn(), clearAnchor: vi.fn() },
    closeFloatingMenus: vi.fn(),
    t: (key) => key,
    addToast,
  })
  const document = () => new DOMParser().parseFromString(editor.getHTML(), 'text/html')
  return { editor, commands, showLinkPopover, addToast, document }
}

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy())
})

describe('usePostEditorLinkCommands with the production TipTap extensions', () => {
  it('updates the entire existing link from a cursor inside it without changing text or formatting', () => {
    const { editor, commands, document, showLinkPopover } = createCommands(
      '<p>Before <a href="https://old.example"><strong>Linked</strong> text</a> after</p>', 10,
    )
    commands.openLinkPopover()
    expect(commands.linkUrl.value).toBe('https://old.example')
    expect(commands.linkText.value).toBe('Linked text')

    commands.applyLink('https://new.example', commands.linkText.value)

    expect(editor.getText()).toBe('Before Linked text after')
    const anchors = document().querySelectorAll('a')
    expect(anchors).toHaveLength(1)
    expect(anchors[0].getAttribute('href')).toBe('https://new.example/')
    expect(anchors[0].textContent).toBe('Linked text')
    expect(anchors[0].querySelector('strong')?.textContent).toBe('Linked')
    expect(showLinkPopover.value).toBe(false)
  })

  it('applies edited display text to selected text and preserves its bold mark', () => {
    const { editor, commands, document } = createCommands(
      '<p>Before <strong>label</strong> after</p>', { from: 8, to: 13 },
    )
    commands.openLinkPopover()
    expect(commands.linkText.value).toBe('label')

    commands.applyLink('https://example.com', 'renamed')

    expect(editor.getText()).toBe('Before renamed after')
    expect(document().querySelector('a')?.textContent).toBe('renamed')
    expect(document().querySelector('strong')?.textContent).toBe('renamed')
  })

  it('replaces an existing link label as a whole from its cursor', () => {
    const { editor, commands, document } = createCommands(
      '<p>Before <a href="https://old.example">old label</a> after</p>', 10,
    )
    commands.openLinkPopover()
    commands.applyLink('https://new.example', 'new label')

    expect(editor.getText()).toBe('Before new label after')
    expect(document().querySelectorAll('a')).toHaveLength(1)
    expect(document().querySelector('a')?.getAttribute('href')).toBe('https://new.example/')
  })

  it('keeps selected text and mixed formatting when only its URL changes', () => {
    const { editor, commands, document } = createCommands(
      '<p><strong>Bold</strong> and <em>italic</em></p>', { from: 1, to: 16 },
    )
    commands.openLinkPopover()
    commands.applyLink('https://example.com', commands.linkText.value)

    expect(editor.getText()).toBe('Bold and italic')
    expect(document().querySelector('strong')?.textContent).toBe('Bold')
    expect(document().querySelector('em')?.textContent).toBe('italic')
    expect(document().querySelector('a')?.textContent).toBe('Bold and italic')
  })

  it('preserves the existing label when the display-text field is empty', () => {
    const { editor, commands, document } = createCommands(
      '<p><a href="https://old.example">Keep this</a></p>', 4,
    )
    commands.openLinkPopover()
    commands.applyLink('https://new.example', '')

    expect(editor.getText()).toBe('Keep this')
    expect(document().querySelector('a')?.getAttribute('href')).toBe('https://new.example/')
  })

  it('inserts a new link at an ordinary cursor with literal display text', () => {
    const { editor, commands, document } = createCommands('<p>Before after</p>', 8)
    commands.openLinkPopover()
    commands.applyLink('https://example.com/?a=1&b=2', '<label> & text')

    expect(editor.getText()).toBe('Before <label> & textafter')
    expect(document().querySelector('a')?.textContent).toBe('<label> & text')
    expect(document().querySelector('label')).toBeNull()
    expect(document().querySelector('a')?.getAttribute('href')).toBe('https://example.com/?a=1&b=2')
  })

  it('uses the URL as the label for a new link without display text', () => {
    const { editor, commands, document } = createCommands('<p></p>', 1)
    commands.openLinkPopover()
    commands.applyLink('https://example.com', '')

    expect(editor.getText()).toBe('https://example.com')
    expect(document().querySelector('a')?.getAttribute('href')).toBe('https://example.com/')
  })

  it('removes the existing link without removing its text or formatting', () => {
    const { editor, commands, document } = createCommands(
      '<p><a href="https://example.com"><strong>Keep this</strong></a></p>', 4,
    )
    commands.openLinkPopover()
    commands.removeLink()

    expect(editor.getText()).toBe('Keep this')
    expect(document().querySelector('a')).toBeNull()
    expect(document().querySelector('strong')?.textContent).toBe('Keep this')
  })

  it('rejects unsafe URLs before changing the existing link', () => {
    const { editor, commands, addToast, showLinkPopover } = createCommands(
      '<p><a href="https://example.com">Keep this</a></p>', 4,
    )
    const before = editor.getHTML()
    commands.openLinkPopover()
    commands.applyLink('javascript:alert(1)', 'changed')

    expect(editor.getHTML()).toBe(before)
    expect(addToast).toHaveBeenCalledWith('board.writePost.invalidLinkUrl', 'error')
    expect(showLinkPopover.value).toBe(true)
  })
})
