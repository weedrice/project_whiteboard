import { Extension, mergeAttributes, Node } from '@tiptap/core'
import { Plugin, TextSelection, type NodeSelection } from '@tiptap/pm/state'
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import RawHtmlBlockView from '@/components/board/editor/RawHtmlBlockView.vue'
import {
  decodeSandboxedPostHtmlPayload,
  encodeSandboxedPostHtmlPayload,
  SANDBOXED_POST_HTML_MARKER_CLASS,
} from '@/utils/postHtmlSandbox'

export const RawHtmlBlock = Node.create({
  name: 'rawHtmlBlock',

  group: 'block',
  atom: true,
  isolating: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      html: {
        default: '',
        parseHTML: (element: HTMLElement) => (
          decodeSandboxedPostHtmlPayload(element.getAttribute('data-value')) ?? ''
        ),
        renderHTML: (attributes: { html?: string | null }) => ({
          'data-value': encodeSandboxedPostHtmlPayload(attributes.html ?? ''),
        }),
      },
    }
  },

  parseHTML() {
    return [{
      tag: `div.${SANDBOXED_POST_HTML_MARKER_CLASS}[data-value]`,
    }]
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        class: SANDBOXED_POST_HTML_MARKER_CLASS,
      }),
    ]
  },

  addNodeView() {
    return VueNodeViewRenderer(RawHtmlBlockView)
  },
})

export const RawHtmlBlockKeyboardNavigation = Extension.create({
  name: 'rawHtmlBlockKeyboardNavigation',
  priority: 1000,

  addKeyboardShortcuts() {
    return {
      Enter: () => {
        const { selection } = this.editor.state
        const selectedNode = 'node' in selection ? (selection as NodeSelection).node : null
        if (!selectedNode || selectedNode.type.name !== 'rawHtmlBlock') {
          return false
        }

        const paragraphPosition = selection.to
        const existingNextNode = this.editor.state.doc.nodeAt(paragraphPosition)
        let transaction = this.editor.state.tr

        if (!existingNextNode?.isTextblock) {
          const paragraph = this.editor.state.schema.nodes.paragraph
          if (!paragraph) return false
          transaction = transaction.insert(paragraphPosition, paragraph.create())
        }

        transaction = transaction
          .setSelection(TextSelection.near(transaction.doc.resolve(paragraphPosition + 1)))
          .scrollIntoView()
        this.editor.view.dispatch(transaction)
        return true
      },
    }
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        appendTransaction: (transactions, _oldState, newState) => {
          if (!transactions.some((transaction) => transaction.docChanged)) return null
          if (newState.doc.lastChild?.type.name !== 'rawHtmlBlock') return null

          const paragraph = newState.schema.nodes.paragraph
          if (!paragraph) return null
          return newState.tr.insert(newState.doc.content.size, paragraph.create())
        },
      }),
    ]
  },
})
