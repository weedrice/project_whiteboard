import { onScopeDispose, watch, type Ref } from 'vue'
import type { Editor, JSONContent } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { closeHistory } from '@tiptap/pm/history'

type AnchorChange = { add?: Array<{ id: number; position: number }>; remove?: number; inserted?: number }

/** Upload positions live outside the document, but follow every document transaction. */
export function usePostEditorUploadAnchors(editor: Ref<Editor | undefined>) {
  const key = new PluginKey<Map<number, number>>('postEditorUploadAnchors')
  const plugin = new Plugin<Map<number, number>>({
    key,
    state: {
      init: () => new Map(),
      apply(transaction, previous) {
        const change = transaction.getMeta(key) as AnchorChange | undefined
        const insertedPosition = change?.inserted === undefined ? undefined : previous.get(change.inserted)
        const anchors = new Map<number, number>()
        previous.forEach((position, id) => {
          if (id === change?.remove || id === change?.inserted) return
          // A later upload can finish before an earlier failed one is retried.
          // Keep their original order when both started at the same position.
          const association = insertedPosition === position && change?.inserted !== undefined && id < change.inserted ? -1 : 1
          const mapped = transaction.mapping.mapResult(position, association)
          if (!mapped.deleted) anchors.set(id, mapped.pos)
        })
        change?.add?.forEach(({ id, position }) => anchors.set(id, position))
        return anchors
      },
    },
  })

  function remove(id: number) {
    const instance = editor.value
    if (!instance || instance.isDestroyed || !key.getState(instance.state)?.has(id)) return
    instance.view.dispatch(instance.state.tr.setMeta(key, { remove: id } satisfies AnchorChange))
  }

  function add(ids: number[], position: number) {
    const instance = editor.value
    if (!instance || instance.isDestroyed || !instance.isEditable || !ids.length) return
    if (!key.getState(instance.state)) instance.registerPlugin(plugin)
    instance.view.dispatch(instance.state.tr.setMeta(key, {
      add: ids.map(id => ({ id, position })),
    } satisfies AnchorChange))
  }

  function has(id: number) {
    const instance = editor.value
    return Boolean(instance && !instance.isDestroyed && key.getState(instance.state)?.has(id))
  }

  function insert(id: number, content: JSONContent) {
    const instance = editor.value
    if (!instance || instance.isDestroyed || !instance.isEditable) return false
    const position = key.getState(instance.state)?.get(id)
    if (position === undefined) return false
    // Do not focus or replace the user's current selection when the upload finishes.
    const previousDocument = instance.state.doc
    const accepted = instance.chain()
      .insertContentAt(position, content, { updateSelection: false })
      .command(({ tr }) => {
        closeHistory(tr).setMeta(key, { inserted: id } satisfies AnchorChange)
        return true
      }).run()
    const inserted = accepted && instance.state.doc !== previousDocument && !key.getState(instance.state)?.has(id)
    if (inserted) instance.view.dispatch(closeHistory(instance.state.tr))
    return inserted
  }

  function detach(instance: Editor | undefined) {
    if (instance && !instance.isDestroyed) instance.unregisterPlugin(key)
  }
  const stop = watch(editor, (_current, previous) => detach(previous), { flush: 'sync' })
  onScopeDispose(() => { stop(); detach(editor.value) }, true)

  return { add, remove, has, insert }
}
