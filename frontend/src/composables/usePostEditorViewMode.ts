import { ref, type Ref } from 'vue'
import { normalizeEditorFileImagePreviewSources } from '@/utils/fileUrl'

export type PostEditorViewMode = 'visual' | 'html'

export function usePostEditorViewMode(content: Ref<string>) {
  const editorViewMode = ref<PostEditorViewMode>('visual')

  const setEditorViewMode = (mode: PostEditorViewMode) => {
    content.value = normalizeEditorFileImagePreviewSources(content.value)
    editorViewMode.value = mode
  }

  const handleEditorViewModeChange = (mode: string) => {
    if (mode === 'visual' || mode === 'html') {
      setEditorViewMode(mode)
    }
  }

  return {
    editorViewMode,
    setEditorViewMode,
    handleEditorViewModeChange,
  }
}
