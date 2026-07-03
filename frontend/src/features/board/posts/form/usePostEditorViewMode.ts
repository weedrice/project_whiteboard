import { ref, type Ref } from 'vue'
import { normalizeEditorFileImagePreviewSources } from '@/utils/fileUrl'
import { requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'

export type PostEditorViewMode = 'visual' | 'html'

export function usePostEditorViewMode(content: Ref<string>) {
  const editorViewMode = ref<PostEditorViewMode>('visual')

  const setEditorViewMode = (mode: PostEditorViewMode) => {
    const isSandboxedHtml = requiresSandboxedPostHtml(content.value)
    content.value = isSandboxedHtml ? content.value : normalizeEditorFileImagePreviewSources(content.value)
    editorViewMode.value = mode === 'visual' && isSandboxedHtml ? 'html' : mode
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
