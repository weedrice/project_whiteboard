import { ref, type Ref } from 'vue'
import { normalizeEditorFileImagePreviewSources } from '@/utils/fileUrl'
import {
  decodeSandboxedPostHtml,
  encodeSandboxedPostHtml,
  expandSandboxedPostHtml,
  requiresPreservedPostHtml,
} from '@/utils/postHtmlSandbox'

export type PostEditorViewMode = 'visual' | 'html'

export function usePostEditorViewMode(content: Ref<string>) {
  const editorViewMode = ref<PostEditorViewMode>('visual')

  const setEditorViewMode = (mode: PostEditorViewMode) => {
    editorViewMode.value = mode
    if (mode === 'visual') {
      content.value = requiresPreservedPostHtml(content.value)
        ? encodeSandboxedPostHtml(content.value)
        : normalizeEditorFileImagePreviewSources(content.value)
    } else {
      const decodedContent = decodeSandboxedPostHtml(content.value)
        ?? expandSandboxedPostHtml(content.value)
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
