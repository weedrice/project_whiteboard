import { mergeAttributes, Node } from '@tiptap/core'
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
