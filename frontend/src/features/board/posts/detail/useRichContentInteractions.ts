import { computed, ref, unref, type MaybeRefOrGetter } from 'vue'
import type { Router } from 'vue-router'

interface CodeCopyLabels {
  copy: string
  copied: string
  failed: string
}

interface UseRichContentInteractionsOptions {
  isDisabled: MaybeRefOrGetter<boolean>
  router: Router
  getCodeCopyLabels: () => CodeCopyLabels
  getImageOpenLabel: (alt: string, index: number) => string
}

export interface RichContentLightboxImage {
  src: string
  alt: string
}

function resolveBoolean(value: MaybeRefOrGetter<boolean>): boolean {
  if (typeof value === 'function') {
    return Boolean((value as () => boolean)())
  }
  return Boolean(unref(value))
}

export function useRichContentInteractions(options: UseRichContentInteractionsOptions) {
  const articleRef = ref<HTMLElement | null>(null)
  const lightboxOpen = ref(false)
  const lightboxIndex = ref(0)
  const lightboxImages = computed(() => {
    if (!articleRef.value || resolveBoolean(options.isDisabled)) return []
    return Array.from(articleRef.value.querySelectorAll<HTMLImageElement>('.nv-rich-content img'))
      .map((image) => ({
        src: image.currentSrc || image.src,
        alt: image.alt.trim(),
      }))
      .filter((image) => Boolean(image.src))
  })

  function prepareImagesForInteraction() {
    if (!articleRef.value || resolveBoolean(options.isDisabled)) return
    articleRef.value.querySelectorAll<HTMLImageElement>('.nv-rich-content img').forEach((image, index) => {
      if (!image.hasAttribute('tabindex')) image.tabIndex = 0
      if (!image.hasAttribute('role')) image.setAttribute('role', 'button')
      image.setAttribute('aria-haspopup', 'dialog')
      image.setAttribute('aria-label', options.getImageOpenLabel(image.alt.trim(), index))
    })
  }

  function navigateToMention(target: EventTarget | null): boolean {
    const mention = (target as HTMLElement | null)?.closest?.('[data-mention-user-id]')
    if (!(mention instanceof HTMLElement)) return false

    const userId = mention.dataset.mentionUserId
    if (!userId) return false

    void options.router.push(`/user/${encodeURIComponent(userId)}`)
    return true
  }

  function copyCodeBlock(target: EventTarget | null): boolean {
    const button = (target as HTMLElement | null)?.closest?.('[data-code-copy-button]')
    if (!(button instanceof HTMLButtonElement)) return false

    const code = button.closest('.nv-code-block')?.querySelector('pre code')
    const codeText = code?.textContent ?? ''
    if (!codeText) return true

    const labels = options.getCodeCopyLabels()
    if (!navigator.clipboard?.writeText) {
      button.textContent = labels.failed
      return true
    }

    navigator.clipboard.writeText(codeText)
      .then(() => {
        const originalText = button.textContent || labels.copy
        button.textContent = labels.copied
        window.setTimeout(() => {
          button.textContent = originalText
        }, 1400)
      })
      .catch(() => {
        button.textContent = labels.failed
      })
    return true
  }

  function openImageLightbox(target: EventTarget | null): boolean {
    const image = (target as HTMLElement | null)?.closest?.('img')
    if (!(image instanceof HTMLImageElement)) return false

    const source = image.currentSrc || image.src
    const index = lightboxImages.value.findIndex((candidate) => candidate.src === source)
    if (index < 0) return false

    image.focus({ preventScroll: true })
    lightboxIndex.value = index
    lightboxOpen.value = true
    return true
  }

  function handleContentClick(event: MouseEvent) {
    if (resolveBoolean(options.isDisabled)) return
    if (navigateToMention(event.target)) return
    if (copyCodeBlock(event.target)) return
    openImageLightbox(event.target)
  }

  function handleContentKeydown(event: KeyboardEvent) {
    if (resolveBoolean(options.isDisabled)) return
    if (event.key === 'Enter' && navigateToMention(event.target)) {
      event.preventDefault()
      return
    }
    if ((event.key === 'Enter' || event.key === ' ') && openImageLightbox(event.target)) {
      event.preventDefault()
    }
  }

  return {
    articleRef,
    lightboxOpen,
    lightboxIndex,
    lightboxImages,
    prepareImagesForInteraction,
    handleContentClick,
    handleContentKeydown,
  }
}
