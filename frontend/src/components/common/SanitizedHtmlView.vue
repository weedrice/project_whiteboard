<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getCurrentQuerySessionSource } from '@/queryAuthScope'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import { createAuthSessionScope } from '@/utils/authSessionScope'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import { AUTHENTICATED_IMAGES_RESET_EVENT, createAuthenticatedImageLoader } from '@/utils/authenticatedImageLoader'
import {
  resolveAuthenticatedFileDisposition,
  resolveAuthenticatedFileRequestPath,
  type AuthenticatedFileDisposition,
} from '@/utils/authenticatedFile'
import { applyImageFallback, DEFAULT_EMOTICON_IMAGE_URL } from '@/utils/imageFallback'
import { asSanitizedHtml, type SanitizedHtml } from '@/utils/sanitize'
import { createImageStateController } from '@/utils/imageState'
import { installImageStateStyles, prepareImagePresentation } from '@/utils/imageStatePresentation'

const props = withDefaults(defineProps<{
  html: SanitizedHtml
  tag?: string
  useImageFallback?: boolean
}>(), {
  tag: 'div',
  useImageFallback: true,
})

const element = ref<HTMLElement | null>(null)
const { t } = useI18n()
if (typeof document !== 'undefined') installImageStateStyles(document)
const imageControllers = new Map<HTMLImageElement, ReturnType<typeof createImageStateController>>()
const AUTHENTICATED_FILE_SRC_ATTRIBUTE = 'data-authenticated-file-src'
const AUTHENTICATED_FILE_HREF_ATTRIBUTE = 'data-authenticated-file-href'
interface AuthenticatedLinkObject extends AuthenticatedFileDisposition {
  objectUrl: string
}

let imageLoader: ReturnType<typeof createAuthenticatedImageLoader> | null = null
let hydrationGeneration = 0
const objectUrls = new Set<string>()
const linkObjectUrls = new Map<HTMLAnchorElement, AuthenticatedLinkObject>()
const linkControllers = new Set<AbortController>()

const renderedHtml = computed(() => {
  if (typeof DOMParser === 'undefined') return props.html

  const document = new DOMParser().parseFromString(props.html, 'text/html')
  document.querySelectorAll<HTMLImageElement>('img').forEach((image) => {
    prepareImagePresentation(image)
    const src = image.getAttribute('src')
    const requestPath = resolveAuthenticatedFileRequestPath(src)
    if (!requestPath) return

    image.setAttribute(AUTHENTICATED_FILE_SRC_ATTRIBUTE, requestPath)
    image.removeAttribute('src')
    image.removeAttribute('srcset')
  })
  document.querySelectorAll<HTMLAnchorElement>('a[href]').forEach((link) => {
    const href = link.getAttribute('href')
    const requestPath = resolveAuthenticatedFileRequestPath(href)
    if (!requestPath) return

    link.setAttribute(AUTHENTICATED_FILE_HREF_ATTRIBUTE, requestPath)
    link.removeAttribute('href')
    link.setAttribute('role', 'link')
    link.setAttribute('tabindex', '0')
  })
  return asSanitizedHtml(document.body.innerHTML)
})

function releaseAuthenticatedFiles() {
  hydrationGeneration += 1
  element.value?.dispatchEvent(new Event(AUTHENTICATED_IMAGES_RESET_EVENT, { bubbles: true }))
  imageLoader?.destroy()
  imageLoader = null
  imageControllers.forEach((controller) => controller.destroy())
  imageControllers.clear()
  element.value?.querySelectorAll<HTMLImageElement>(`img[${AUTHENTICATED_FILE_SRC_ATTRIBUTE}]`).forEach((image) => {
    prepareImagePresentation(image)
    image.removeAttribute('src')
    image.removeAttribute('srcset')
    delete image.dataset.fallbackApplied
  })
  linkControllers.forEach((controller) => controller.abort())
  linkControllers.clear()
  linkObjectUrls.clear()
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.clear()
}

async function hydrateAuthenticatedFiles() {
  releaseAuthenticatedFiles()
  const generation = hydrationGeneration
  const source = getCurrentQuerySessionSource()
  const intent = captureAuthSessionIntent(source)
  await nextTick()

  if (generation !== hydrationGeneration || !isAuthSessionIntentCurrent(source, intent)) return
  const root = element.value
  if (!root) return
  root.querySelectorAll<HTMLImageElement>('img').forEach((image) => {
    imageControllers.set(image, createImageStateController(image, () => t('common.imageLoadError')))
  })

  imageLoader = createAuthenticatedImageLoader(root, (image, state) => {
    if (state === 'error' && shouldUseEmoticonFallback(image)) {
      image.src = DEFAULT_EMOTICON_IMAGE_URL
      imageControllers.get(image)?.refresh({})
    } else {
      imageControllers.get(image)?.refresh({ pending: state === 'loading', failed: state === 'error' })
    }
  })
}
function shouldUseEmoticonFallback(image: HTMLImageElement): boolean {
  return props.useImageFallback && image.matches('.ql-emoticon, .comment-emoticon, .comment-emoticon-list')
}

function handleImageError(event: Event) {
  const image = event.target
  if (image instanceof HTMLImageElement && shouldUseEmoticonFallback(image)) {
    applyImageFallback(event)
  }
}

async function activateAuthenticatedFile(event: MouseEvent | KeyboardEvent) {
  const target = event.target
  const root = element.value
  if (!(target instanceof Element) || !root) return

  const link = target.closest<HTMLAnchorElement>(`a[${AUTHENTICATED_FILE_HREF_ATTRIBUTE}]`)
  if (!link || !root.contains(link)) return
  if (event instanceof MouseEvent && event.button !== 0) return

  event.preventDefault()
  if (link.getAttribute('aria-busy') === 'true') return

  const cachedObjectUrl = linkObjectUrls.get(link)
  if (cachedObjectUrl) {
    followAuthenticatedFileLink(link, cachedObjectUrl, event)
    return
  }

  const requestPath = link.getAttribute(AUTHENTICATED_FILE_HREF_ATTRIBUTE)
  if (!requestPath) return

  const generation = hydrationGeneration
  const scope = createAuthSessionScope(getCurrentQuerySessionSource())
  const controller = new AbortController()
  linkControllers.add(controller)
  link.setAttribute('aria-busy', 'true')
  link.removeAttribute('aria-disabled')

  try {
    const { default: api } = await import('@/api')
    if (controller.signal.aborted || !scope.isCurrent()) return
    const response = await api.get<Blob>(requestPath, scope.withRequestConfig({
      responseType: 'blob',
      signal: controller.signal,
      skipGlobalErrorHandler: true,
    }))
    if (controller.signal.aborted || !scope.isCurrent() || generation !== hydrationGeneration || !root.contains(link)) return

    const objectUrl = URL.createObjectURL(response.data)
    const disposition = resolveAuthenticatedFileDisposition(response.headers?.['content-disposition'])
    const authenticatedLinkObject = { objectUrl, ...disposition }
    objectUrls.add(objectUrl)
    linkObjectUrls.set(link, authenticatedLinkObject)
    followAuthenticatedFileLink(link, authenticatedLinkObject, event)
  } catch {
    if (!controller.signal.aborted && scope.isCurrent() && generation === hydrationGeneration && root.contains(link)) {
      link.setAttribute('aria-disabled', 'true')
    }
  } finally {
    scope.dispose()
    linkControllers.delete(controller)
    if (root.contains(link)) link.removeAttribute('aria-busy')
  }
}

function followAuthenticatedFileLink(
  source: HTMLAnchorElement,
  file: AuthenticatedLinkObject,
  event: MouseEvent | KeyboardEvent,
) {
  const link = document.createElement('a')
  link.href = file.objectUrl
  if (file.forceDownload) link.download = file.fileName ?? ''
  const requestedTarget = source.getAttribute('target')
  const openInNewTab = event instanceof MouseEvent && (event.ctrlKey || event.metaKey || event.shiftKey)
  if (requestedTarget) link.target = requestedTarget
  if (openInNewTab) link.target = '_blank'
  const rel = source.getAttribute('rel')
  if (rel) link.rel = rel
  link.click()
}

watch(() => t('common.imageLoadError'), () => {
  imageControllers.forEach((controller) => controller.refreshErrorLabel())
})

watch(renderedHtml, () => {
  void hydrateAuthenticatedFiles()
}, { flush: 'post' })

onMounted(() => {
  void hydrateAuthenticatedFiles()
})

const stopSessionBoundary = useAuthSessionReset(() => {
  void hydrateAuthenticatedFiles()
}, getCurrentQuerySessionSource())

onBeforeUnmount(() => {
  stopSessionBoundary()
  releaseAuthenticatedFiles()
})

defineExpose({
  element,
})
</script>

<template>
  <component
    :is="tag"
    ref="element"
    :innerHTML="renderedHtml"
    @click="activateAuthenticatedFile"
    @keydown.enter="activateAuthenticatedFile"
    @error.capture="handleImageError"
  />
</template>
