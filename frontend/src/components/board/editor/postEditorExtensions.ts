import { mergeAttributes } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight'
import Mention from '@tiptap/extension-mention'
import Underline from '@tiptap/extension-underline'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import Highlight from '@tiptap/extension-highlight'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import TextAlign from '@tiptap/extension-text-align'
import { TableKit } from '@tiptap/extension-table'
import HorizontalRule from '@tiptap/extension-horizontal-rule'
import { FontSize, LineHeight } from '@tiptap/extension-text-style'
import { Video } from '@/extensions/tiptap-video'
import { userAccountApi } from '@/api/userAccountApi'
import { unwrapAxiosApiData } from '@/api/response'
import { lowlight } from '@/utils/codeHighlighting'
import type { MentionCandidate } from '@/types'

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

function createMentionListRenderer() {
  let element: HTMLDivElement | null = null
  let selectedIndex = 0
  let items: MentionCandidate[] = []
  let command: ((item: MentionCandidate) => void) | null = null

  function updateSelection() {
    element?.querySelectorAll<HTMLButtonElement>('button').forEach((button, index) => {
      button.dataset.active = index === selectedIndex ? 'true' : 'false'
    })
  }

  function render() {
    if (!element) return
    element.innerHTML = ''
    items.forEach((item, index) => {
      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'mention-suggestion-item'
      button.textContent = item.displayName
      button.addEventListener('mousedown', (event) => {
        event.preventDefault()
        command?.(item)
      })
      element?.appendChild(button)
      if (index === selectedIndex) {
        button.dataset.active = 'true'
      }
    })
  }

  function position(clientRect?: (() => DOMRect | null) | null) {
    if (!element || !clientRect) return
    const rect = clientRect()
    if (!rect) return
    element.style.left = `${rect.left + window.scrollX}px`
    element.style.top = `${rect.bottom + window.scrollY + 6}px`
  }

  return {
    onStart: (props: any) => {
      selectedIndex = 0
      items = props.items
      command = props.command
      element = document.createElement('div')
      element.className = 'mention-suggestion-menu'
      document.body.appendChild(element)
      render()
      position(props.clientRect)
    },
    onUpdate: (props: any) => {
      selectedIndex = 0
      items = props.items
      command = props.command
      render()
      position(props.clientRect)
    },
    onKeyDown: ({ event }: { event: KeyboardEvent }) => {
      if (!items.length) return false
      if (event.key === 'ArrowDown') {
        selectedIndex = (selectedIndex + 1) % items.length
        updateSelection()
        return true
      }
      if (event.key === 'ArrowUp') {
        selectedIndex = (selectedIndex - 1 + items.length) % items.length
        updateSelection()
        return true
      }
      if (event.key === 'Enter') {
        command?.(items[selectedIndex])
        return true
      }
      return false
    },
    onExit: () => {
      element?.remove()
      element = null
      command = null
      items = []
    },
  }
}

export function createPostEditorExtensions() {
  return [
    StarterKit.configure({
      codeBlock: false,
      heading: { levels: [1, 2, 3, 4, 5, 6] },
      horizontalRule: false,
      link: false,
      underline: false,
    }),
    CodeBlockLowlight.configure({
      lowlight,
      HTMLAttributes: {
        class: 'hljs',
      },
    }),
    Mention.configure({
      HTMLAttributes: {
        class: 'mention-node',
      },
      renderText({ node }) {
        return `@${node.attrs.label ?? node.attrs.id}`
      },
      renderHTML({ node, options }) {
        return [
          'span',
          mergeAttributes(options.HTMLAttributes, {
            'data-type': 'mention',
            'data-mention-user-id': node.attrs.id,
          }),
          `@${node.attrs.label ?? node.attrs.id}`,
        ]
      },
      suggestion: {
        char: '@',
        items: async ({ query }) => {
          if (!query.trim()) return []
          const response = await userAccountApi.getMentionCandidates(query)
          return unwrapAxiosApiData(response)
        },
        command: ({ editor, range, props }) => {
          const item = props as unknown as MentionCandidate
          editor.chain().focus().insertContentAt(range, [
            {
              type: 'mention',
              attrs: {
                id: String(item.userId),
                label: item.displayName,
              },
            },
            { type: 'text', text: ' ' },
          ]).run()
        },
        render: createMentionListRenderer,
      },
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
  ]
}
