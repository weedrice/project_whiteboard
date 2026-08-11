import { Extension, mergeAttributes, Node, type Editor } from '@tiptap/core'
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
      Enter: () => moveFromSelectedRawHtmlBlock(this.editor, 1),
      ArrowRight: () => moveFromSelectedRawHtmlBlock(this.editor, 1),
      ArrowDown: () => moveFromSelectedRawHtmlBlock(this.editor, 1),
      ArrowLeft: () => moveFromSelectedRawHtmlBlock(this.editor, -1),
      ArrowUp: () => moveFromSelectedRawHtmlBlock(this.editor, -1),
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

export function isRawHtmlBlockNodeSelected(editor: Editor): boolean {
  const { selection } = editor.state
  const selectedNode = 'node' in selection ? (selection as NodeSelection).node : null
  return selectedNode?.type.name === 'rawHtmlBlock'
}

export function moveFromSelectedRawHtmlBlock(editor: Editor, direction: -1 | 1): boolean {
  const { selection, doc, schema } = editor.state
  if (!isRawHtmlBlockNodeSelected(editor)) return false

  const boundaryPosition = direction === 1 ? selection.to : selection.from
  const adjacentNode = direction === 1
    ? doc.nodeAt(boundaryPosition)
    : doc.resolve(boundaryPosition).nodeBefore
  let transaction = editor.state.tr

  if (!adjacentNode?.isTextblock) {
    const paragraph = schema.nodes.paragraph
    if (!paragraph) return false
    transaction = transaction.insert(boundaryPosition, paragraph.create())
  }

  transaction = transaction
    .setSelection(TextSelection.near(transaction.doc.resolve(boundaryPosition), direction))
    .scrollIntoView()
  editor.view.dispatch(transaction)
  return true
}
