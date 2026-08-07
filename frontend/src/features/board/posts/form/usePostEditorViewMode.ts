import { ref, type Ref } from 'vue'
import { normalizeEditorFileImagePreviewSources } from '@/utils/fileUrl'
import {
  decodeSandboxedPostHtml,
  encodeSandboxedPostHtml,
  requiresSandboxedPostHtml,
} from '@/utils/postHtmlSandbox'

export type PostEditorViewMode = 'visual' | 'html'

export function usePostEditorViewMode(content: Ref<string>) {
  const editorViewMode = ref<PostEditorViewMode>('visual')

  const setEditorViewMode = (mode: PostEditorViewMode) => {
    editorViewMode.value = mode
    if (mode === 'visual') {
      content.value = requiresSandboxedPostHtml(content.value)
        ? encodeSandboxedPostHtml(content.value)
        : normalizeEditorFileImagePreviewSources(content.value)
    } else {
      const decodedContent = decodeStandaloneSandboxedHtml(content.value)
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

function decodeStandaloneSandboxedHtml(content: string): string | null {
  const decoded = decodeSandboxedPostHtml(content)
  if (decoded == null || typeof DOMParser === 'undefined') return decoded

  const doc = new DOMParser().parseFromString(content, 'text/html')
  const meaningfulNodes = Array.from(doc.body.childNodes).filter((node) => (
    node.nodeType !== Node.TEXT_NODE || Boolean(node.textContent?.trim())
  ))
  if (meaningfulNodes.length !== 1) return null

  const onlyNode = meaningfulNodes[0]
  return onlyNode instanceof HTMLElement && onlyNode.classList.contains('noviis-sandboxed-post-html')
    ? decoded
    : null
}
