import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import { findPostEditorText, type PostEditorTextMatch } from './postEditorDocumentTools'

interface SearchState {
  query: string
  matches: PostEditorTextMatch[]
  decorations: DecorationSet
}
export const postEditorSearchKey = new PluginKey<SearchState>('postEditorSearch')

/** Decorations are view-only: search never changes saved HTML or undo history. */
export const PostEditorSearch = Extension.create({
  name: 'postEditorSearch',
  addProseMirrorPlugins() {
    return [new Plugin<SearchState>({
      key: postEditorSearchKey,
      state: {
        init: () => ({ query: '', matches: [], decorations: DecorationSet.empty }),
        apply(tr, previous) {
          const query: string = tr.getMeta(postEditorSearchKey) ?? previous.query
          if (query === previous.query && !tr.docChanged && !tr.selectionSet) return previous
          const matches = query !== previous.query || tr.docChanged
            ? findPostEditorText(tr.doc, query) : previous.matches
          const decorations = DecorationSet.create(tr.doc, matches.map(match => Decoration.inline(match.from, match.to, {
            class: match.from === tr.selection.from && match.to === tr.selection.to
              ? 'post-editor-search-match post-editor-search-match--current' : 'post-editor-search-match',
          })))
          return { query, matches, decorations }
        },
      },
      props: { decorations: state => postEditorSearchKey.getState(state)?.decorations },
    })]
  },
})
