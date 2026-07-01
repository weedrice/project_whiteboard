import { ref, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'

export function usePostEditorListCommands(editor: Ref<Editor | undefined>) {
  const savedListSelection = ref<{ from: number; to: number } | null>(null)

  function saveListSelection() {
    const instance = editor.value
    if (!instance) return
    const { from, to } = instance.state.selection
    savedListSelection.value = from !== to ? { from, to } : null
  }

  function applyListToggle(type: 'bullet' | 'ordered') {
    const instance = editor.value
    if (!instance) return
    const chain = instance.chain().focus()
    const saved = savedListSelection.value
    if (saved) {
      chain.setTextSelection({ from: saved.from, to: saved.to })
      savedListSelection.value = null
    }

    if (type === 'bullet') {
      chain.toggleBulletList().run()
    } else {
      chain.toggleOrderedList().run()
    }
  }

  return {
    saveListSelection,
    applyBulletList: () => applyListToggle('bullet'),
    applyOrderedList: () => applyListToggle('ordered'),
  }
}
