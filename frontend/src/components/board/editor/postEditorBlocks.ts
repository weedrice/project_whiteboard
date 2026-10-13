import { Node, mergeAttributes } from '@tiptap/core'
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import PostEditorTaskItemView from './PostEditorTaskItemView.vue'

export const PostEditorTaskList = Node.create({
  name: 'taskList', group: 'block list', content: 'taskItem+',
  parseHTML: () => [{ tag: 'ul[data-type="taskList"]', priority: 100 }],
  renderHTML: () => ['ul', { 'data-type': 'taskList' }, 0],
})

export const PostEditorTaskItem = Node.create({
  name: 'taskItem', content: 'paragraph block*', defining: true,
  addAttributes: () => ({ checked: {
    default: false, keepOnSplit: false,
    parseHTML: element => element.getAttribute('data-checked') === 'true',
    renderHTML: attrs => ({ 'data-checked': String(Boolean(attrs.checked)) }),
  } }),
  parseHTML: () => [{ tag: 'li[data-type="taskItem"]', priority: 100 }],
  renderHTML: ({ HTMLAttributes }) => ['li', mergeAttributes(HTMLAttributes, { 'data-type': 'taskItem' }), 0],
  addNodeView: () => VueNodeViewRenderer(PostEditorTaskItemView),
  addKeyboardShortcuts() {
    return {
      Enter: () => this.editor.commands.splitListItem('taskItem'),
      Tab: () => this.editor.commands.sinkListItem('taskItem'),
      'Shift-Tab': () => this.editor.commands.liftListItem('taskItem'),
    }
  },
})

export const PostEditorCallout = Node.create({
  name: 'callout', group: 'block', content: 'block+', defining: true,
  addAttributes: () => ({ kind: {
    default: 'info',
    parseHTML: element => element.getAttribute('data-callout') === 'warning' ? 'warning' : 'info',
    renderHTML: attrs => ({ 'data-callout': attrs.kind === 'warning' ? 'warning' : 'info' }),
  } }),
  parseHTML: () => [{ tag: 'aside[data-callout]' }],
  renderHTML: ({ HTMLAttributes }) => ['aside', HTMLAttributes, 0],
})

export const PostEditorDetails = Node.create({
  name: 'details', group: 'block', content: 'detailsSummary detailsContent', defining: true, isolating: true,
  parseHTML: () => [{ tag: 'details' }],
  renderHTML: () => ['details', {}, 0],
  addNodeView() {
    return () => {
      const dom = document.createElement('details')
      dom.open = true
      dom.addEventListener('click', event => {
        if ((event.target as HTMLElement).closest('summary')?.parentElement === dom) event.preventDefault()
      })
      return { dom, contentDOM: dom, ignoreMutation: mutation => mutation.type === 'attributes' && mutation.attributeName === 'open' }
    }
  },
})
export const PostEditorDetailsSummary = Node.create({
  name: 'detailsSummary', content: 'inline*', defining: true,
  parseHTML: () => [{ tag: 'summary' }],
  renderHTML: () => ['summary', {}, 0],
})
export const PostEditorDetailsContent = Node.create({
  name: 'detailsContent', content: 'block+', defining: true,
  parseHTML: () => [{ tag: 'div[data-details-content]' }],
  renderHTML: () => ['div', { 'data-details-content': '' }, 0],
})

export const postEditorBlocks = [PostEditorTaskList, PostEditorTaskItem, PostEditorCallout, PostEditorDetails, PostEditorDetailsSummary, PostEditorDetailsContent]
