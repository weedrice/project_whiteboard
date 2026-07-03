import { onUnmounted, ref } from 'vue'
import { useEventListener } from '@/composables/useEventListener'
import { usePopoverFocus } from '@/composables/usePopoverFocus'
import { getViewportSize, matchesMediaQuery } from '@/utils/browserEnv'
import { toEmbedPostVideoUrl } from '@/utils/postForm'
import type { EmoticonImage } from '@/types/emoticon'

type ComposerToastType = 'info' | 'success' | 'warning' | 'error'
export type ComposerEditor = {
  setVideo: (url: string) => void
  setEmoticon: (image: EmoticonImage) => void
}

type UsePostComposerEffectsOptions = {
  t: (key: string) => string
  addToast: (message: string, type: ComposerToastType) => void
  handleSubmit: () => void | Promise<void>
  handleSaveDraft: () => void | Promise<void>
  handleCancel: () => void
  onBeforeUnload: (event: BeforeUnloadEvent) => string | void
}

function isMobileView(): boolean {
  return matchesMediaQuery('(max-width: 767px)')
}

export function usePostComposerEffects(options: UsePostComposerEffectsOptions) {
  const tiptapEditorRef = ref<ComposerEditor | null>(null)
  const editorWrapperRef = ref<HTMLElement | null>(null)
  const composePageRef = ref<HTMLElement | null>(null)
  const videoPopoverRef = ref<HTMLElement | null>(null)

  const showPreview = ref(false)
  const showEmoticonPicker = ref(false)
  const showVideoPopover = ref(false)
  const videoUrl = ref('')
  const videoPopoverStyle = ref<{ top: string; left: string }>({ top: '0', left: '0' })
  const isEditorFocusWithin = ref(false)

  usePopoverFocus(videoPopoverRef, showVideoPopover)

  function openVideoPopover() {
    if (typeof window === 'undefined') {
      videoPopoverStyle.value = { top: '300px', left: '400px' }
    } else if (!isMobileView() && editorWrapperRef.value) {
      const toolbar = editorWrapperRef.value.querySelector('.tiptap-toolbar')
      if (toolbar) {
        const rect = toolbar.getBoundingClientRect()
        videoPopoverStyle.value = {
          top: `${rect.bottom + 8}px`,
          left: `${rect.left + rect.width / 2}px`,
        }
      } else {
        const rect = editorWrapperRef.value.getBoundingClientRect()
        videoPopoverStyle.value = {
          top: `${rect.top + 60}px`,
          left: `${rect.left + rect.width / 2}px`,
        }
      }
    } else {
      const { width, height } = getViewportSize()
      videoPopoverStyle.value = {
        top: `${height / 2}px`,
        left: `${width / 2}px`,
      }
    }
    videoUrl.value = ''
    showVideoPopover.value = true
  }

  function closeVideoPopover() {
    showVideoPopover.value = false
    videoUrl.value = ''
  }

  function insertVideoFromPopover() {
    const rawVideoUrl = videoUrl.value.trim()
    if (!rawVideoUrl) {
      options.addToast(options.t('board.writePost.videoUrlRequired'), 'error')
      return
    }

    const embedUrl = toEmbedPostVideoUrl(rawVideoUrl)
    if (!embedUrl) {
      options.addToast(options.t('board.writePost.invalidVideoUrl'), 'error')
      return
    }
    tiptapEditorRef.value?.setVideo(embedUrl)
    closeVideoPopover()
  }

  function handleEmoticonSelect(image: EmoticonImage) {
    tiptapEditorRef.value?.setEmoticon(image)
    showEmoticonPicker.value = false
  }

  function syncEditorFocus(value: boolean) {
    if (typeof window === 'undefined') return
    window.dispatchEvent(new CustomEvent('noviis:editor-focus-change', { detail: value }))
  }

  let focusOutTimerId: number | undefined

  function clearFocusOutTimer() {
    if (focusOutTimerId === undefined || typeof window === 'undefined') return
    window.clearTimeout(focusOutTimerId)
    focusOutTimerId = undefined
  }

  function handleFocusIn() {
    clearFocusOutTimer()
    isEditorFocusWithin.value = true
    syncEditorFocus(true)
  }

  function handleFocusOut() {
    clearFocusOutTimer()
    if (typeof window === 'undefined') return
    focusOutTimerId = window.setTimeout(() => {
      focusOutTimerId = undefined
      if (!composePageRef.value?.contains(document.activeElement)) {
        isEditorFocusWithin.value = false
        syncEditorFocus(false)
      }
    }, 0)
  }

  function handleKeyDown(event: KeyboardEvent) {
    const { key, ctrlKey, metaKey } = event
    if ((ctrlKey || metaKey) && key === 'Enter') {
      event.preventDefault()
      void options.handleSubmit()
      return
    }
    if ((ctrlKey || metaKey) && (key === 's' || key === 'S')) {
      event.preventDefault()
      void options.handleSaveDraft()
      return
    }
    if (key === 'Escape') {
      if (showVideoPopover.value) {
        event.preventDefault()
        closeVideoPopover()
        return
      }
      if (showEmoticonPicker.value) {
        event.preventDefault()
        showEmoticonPicker.value = false
        return
      }
      if (showPreview.value) {
        event.preventDefault()
        showPreview.value = false
        return
      }
      event.preventDefault()
      options.handleCancel()
    }
  }

  useEventListener(() => document, 'keydown', handleKeyDown)
  useEventListener<BeforeUnloadEvent>(() => window, 'beforeunload', options.onBeforeUnload)

  onUnmounted(() => {
    clearFocusOutTimer()
    syncEditorFocus(false)
  })

  return {
    tiptapEditorRef,
    editorWrapperRef,
    composePageRef,
    videoPopoverRef,
    showPreview,
    showEmoticonPicker,
    showVideoPopover,
    videoUrl,
    videoPopoverStyle,
    isEditorFocusWithin,
    openVideoPopover,
    closeVideoPopover,
    insertVideoFromPopover,
    handleEmoticonSelect,
    handleFocusIn,
    handleFocusOut,
  }
}
