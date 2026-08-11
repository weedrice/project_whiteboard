import { computed, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'

type TextAlign = 'left' | 'center' | 'right' | 'justify'

export const usePostEditorTextCommands = (editor: Ref<Editor | undefined>) => {
  const currentTextColor = computed(() => editor.value?.getAttributes('textStyle').color || '')
  const isDefaultColor = computed(() => !currentTextColor.value)
  const currentFontSize = computed(() => editor.value?.getAttributes('textStyle').fontSize || '')
  const currentLineHeight = computed(() => editor.value?.getAttributes('textStyle').lineHeight || '')

  const isTextAlignActive = (align: TextAlign) => (
    editor.value?.isActive({ textAlign: align }) ?? false
  )

  const activeTextAlign = computed<TextAlign | ''>(() => {
    if (isTextAlignActive('left')) return 'left'
    if (isTextAlignActive('center')) return 'center'
    if (isTextAlignActive('right')) return 'right'
    if (isTextAlignActive('justify')) return 'justify'
    return ''
  })

  const applyFontSize = (value: string) => {
    if (!editor.value) return
    if (value) {
      editor.value.chain().focus().setFontSize(value).run()
      return
    }
    editor.value.chain().focus().unsetFontSize().run()
  }

  const applyLineHeight = (value: string) => {
    if (!editor.value) return
    if (value) {
      editor.value.chain().focus().setLineHeight(value).run()
      return
    }
    editor.value.chain().focus().unsetLineHeight().run()
  }

  const applyHorizontalRule = () => {
    editor.value?.chain().focus().setHorizontalRule().run()
  }

  const setTextAlign = (align: TextAlign) => {
    editor.value?.chain().focus().setTextAlign(align).run()
  }

  return {
    currentTextColor,
    isDefaultColor,
    currentFontSize,
    currentLineHeight,
    activeTextAlign,
    applyFontSize,
    applyLineHeight,
    applyHorizontalRule,
    setTextAlign,
  }
}
