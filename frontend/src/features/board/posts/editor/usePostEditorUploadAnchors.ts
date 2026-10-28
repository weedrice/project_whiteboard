import { onScopeDispose, watch, type Ref } from 'vue'
import type { Editor, JSONContent } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { closeHistory } from '@tiptap/pm/history'

type UploadAnchor = { position: number; association: -1 | 1 }

type AnchorChange = { add?: Array<{ id: number; position: number }>; remove?: number; inserted?: number }

/** Upload positions live outside the document, but follow every document transaction. */
export function usePostEditorUploadAnchors(editor: Ref<Editor | undefined>) {
  const key = new PluginKey<Map<number, UploadAnchor>>('postEditorUploadAnchors')
  const plugin = new Plugin<Map<number, UploadAnchor>>({
    key,
    state: {
      init: () => new Map(),
      apply(transaction, previous) {
        const change = transaction.getMeta(key) as AnchorChange | undefined
        const insertedPosition = change?.inserted === undefined ? undefined : previous.get(change.inserted)?.position
        const anchors = new Map<number, UploadAnchor>()
        // Drop positions may be on either document boundary, where deletedAcross
        // is false even when setContent replaces the entire document.
        const replacesDocument = transaction.mapping.maps.some((map, index) => {
          const documentSize = transaction.docs[index]?.content.size
          let replacesWholeDocument = false
          map.forEach((from, to) => {
            if (from === 0 && to > 0 && to === documentSize) replacesWholeDocument = true
          })
          return replacesWholeDocument
        })
        previous.forEach((anchor, id) => {
          const { position } = anchor
          if (replacesDocument || id === change?.remove || id === change?.inserted) return
          // A later upload can finish before an earlier failed one is retried.
          // Persist the earlier anchor's left affinity: history redo replays the
          // image insertion without our inserted-id metadata.
          const association = insertedPosition === position && change?.inserted !== undefined
            ? (id < change.inserted ? -1 : 1) : anchor.association
          const mapped = transaction.mapping.mapResult(position, association)
          // Removing an adjacent image (including undo) does not delete this
          // insertion point. Only a deletion spanning the point invalidates it.
          if (!mapped.deletedAcross) anchors.set(id, { position: mapped.pos, association })
        })
        change?.add?.forEach(({ id, position }) => anchors.set(id, { position, association: 1 }))
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
    const position = key.getState(instance.state)?.get(id)?.position
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
