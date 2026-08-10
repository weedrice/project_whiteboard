import { mergeAttributes } from '@tiptap/core'
import { VueRenderer } from '@tiptap/vue-3'
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
import { RawHtmlBlock } from '@/extensions/tiptap-raw-html-block'
import MentionSuggestionList from '@/features/mentions/MentionSuggestionList.vue'
import { createMentionCandidateLookup } from '@/features/mentions/useMentionAutocomplete'
import { lowlight } from '@/utils/codeHighlighting'
import type { MentionCandidate } from '@/types'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'

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

let mentionListIdSequence = 0

function createMentionListRenderer(lookup: ReturnType<typeof createMentionCandidateLookup>) {
  let renderer: VueRenderer | null = null
  let element: HTMLElement | null = null
  let selectedIndex = 0
  let items: MentionCandidate[] = []
  let command: ((item: MentionCandidate) => void) | null = null
  let editorElement: HTMLElement | null = null
  let stopSessionBoundary: (() => void) | null = null
  const listId = `post-editor-mention-listbox-${++mentionListIdSequence}`

  function syncEditorAria() {
    if (!editorElement) return
    editorElement.setAttribute('role', 'combobox')
    editorElement.setAttribute('aria-autocomplete', 'list')
    editorElement.setAttribute('aria-haspopup', 'listbox')
    editorElement.setAttribute('aria-expanded', String(
      items.length > 0 || lookup.isLoading.value || !!lookup.error.value,
    ))
    editorElement.setAttribute('aria-controls', listId)
    const candidate = items[selectedIndex]
    if (candidate) {
      editorElement.setAttribute('aria-activedescendant', `${listId}-option-${candidate.userId}`)
    } else {
      editorElement.removeAttribute('aria-activedescendant')
    }
  }

  function updateRenderer() {
    renderer?.updateProps({
      items,
      selectedIndex,
      id: listId,
      onSelect: (item: MentionCandidate) => {
        command?.(item)
      },
      loading: lookup.isLoading.value,
      error: !!lookup.error.value,
      onRetry: () => {
        const promise = lookup.retry()
        updateRenderer()
        void promise.then((candidates) => {
          items = candidates
        }).catch(() => undefined).finally(updateRenderer)
      },
    })
    syncEditorAria()
  }

  function position(clientRect?: (() => DOMRect | null) | null) {
    if (!element || !clientRect) return
    const rect = clientRect()
    if (!rect) return
    element.style.left = `${rect.left + window.scrollX}px`
    element.style.top = `${rect.bottom + window.scrollY + 6}px`
  }

  function cleanup() {
    lookup.cancel()
    stopSessionBoundary?.()
    stopSessionBoundary = null
    editorElement?.setAttribute('aria-expanded', 'false')
    editorElement?.removeAttribute('aria-activedescendant')
    editorElement?.removeAttribute('aria-controls')
    renderer?.destroy()
    element?.remove()
    element = null
    renderer = null
    command = null
    items = []
    editorElement = null
  }

  return {
    onStart: (props: any) => {
      selectedIndex = 0
      items = props.items
      command = props.command
      editorElement = props.editor.view.dom as HTMLElement
      renderer = new VueRenderer(MentionSuggestionList, {
        editor: props.editor,
        props: {
          items,
          selectedIndex,
          id: listId,
          onSelect: (item: MentionCandidate) => {
            command?.(item)
          },
          loading: lookup.isLoading.value,
          error: !!lookup.error.value,
          onRetry: () => {
            const promise = lookup.retry()
            updateRenderer()
            void promise.then((candidates) => {
              items = candidates
            }).catch(() => undefined).finally(updateRenderer)
          },
        },
      })
      element = renderer.element as HTMLElement
      element.style.position = 'absolute'
      element.style.zIndex = 'var(--nv-z-popup)'
      element.style.right = 'auto'
      element.style.bottom = 'auto'
      document.body.appendChild(element)
      stopSessionBoundary = subscribeAuthSessionBoundary(cleanup)
      syncEditorAria()
      position(props.clientRect)
    },
    onUpdate: (props: any) => {
      selectedIndex = 0
      items = props.items
      command = props.command
      updateRenderer()
      position(props.clientRect)
    },
    onKeyDown: ({ event }: { event: KeyboardEvent }) => {
      if (!items.length) return false
      if (event.key === 'ArrowDown') {
        selectedIndex = (selectedIndex + 1) % items.length
        updateRenderer()
        return true
      }
      if (event.key === 'ArrowUp') {
        selectedIndex = (selectedIndex - 1 + items.length) % items.length
        updateRenderer()
        return true
      }
      if (event.key === 'Enter') {
        command?.(items[selectedIndex])
        return true
      }
      if (event.key === 'Escape') {
        cleanup()
        return true
      }
      return false
    },
    onExit: () => {
      cleanup()
    },
  }
}

export function createPostEditorExtensions() {
  const mentionLookup = createMentionCandidateLookup()
  return [
    StarterKit.configure({
      codeBlock: false,
      heading: { levels: [1, 2, 3, 4, 5, 6] },
      horizontalRule: false,
      link: false,
      underline: false,
    }),
    RawHtmlBlock,
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
            'data-id': node.attrs.id,
            'data-label': node.attrs.label,
            'data-mention-user-id': node.attrs.id,
          }),
          `@${node.attrs.label ?? node.attrs.id}`,
        ]
      },
      suggestion: {
        char: '@',
        items: async ({ query }) => {
          if (!query.trim()) return []
          try {
            return await mentionLookup.search(query)
          } catch {
            return []
          }
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
        render: () => createMentionListRenderer(mentionLookup),
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
        rel: 'nofollow noopener noreferrer',
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
