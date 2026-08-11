import { ref, type Ref } from 'vue'
import { normalizeEditorFileImagePreviewSources } from '@/utils/fileUrl'
import {
  decodeSandboxedPostHtml,
  encodeSandboxedPostHtml,
  expandSandboxedPostHtmlForEditing,
  requiresPreservedPostHtml,
  restoreSandboxedPostHtmlAfterEditing,
} from '@/utils/postHtmlSandbox'

export type PostEditorViewMode = 'visual' | 'html'

export function usePostEditorViewMode(content: Ref<string>) {
  const editorViewMode = ref<PostEditorViewMode>('visual')

  const setEditorViewMode = (mode: PostEditorViewMode) => {
    editorViewMode.value = mode
    if (mode === 'visual') {
      const restoredContent = restoreSandboxedPostHtmlAfterEditing(content.value)
      content.value = requiresPreservedPostHtml(restoredContent)
        ? encodeSandboxedPostHtml(restoredContent)
        : normalizeEditorFileImagePreviewSources(restoredContent)
    } else {
      const decodedContent = decodeSandboxedPostHtml(content.value)
        ?? expandSandboxedPostHtmlForEditing(content.value)
      content.value = decodedContent ?? normalizeEditorFileImagePreviewSources(content.value)
    }
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
