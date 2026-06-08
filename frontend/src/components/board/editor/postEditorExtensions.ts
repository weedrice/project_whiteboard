import StarterKit from '@tiptap/starter-kit'
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

export function createPostEditorExtensions() {
  return [
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
  ]
}
