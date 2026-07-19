<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { applyImageFallback } from '@/utils/imageFallback'
import { asSanitizedHtml, type SanitizedHtml } from '@/utils/sanitize'

const props = withDefaults(defineProps<{
  html: SanitizedHtml
  tag?: string
  useImageFallback?: boolean
}>(), {
  tag: 'div',
  useImageFallback: true,
})

const element = ref<HTMLElement | null>(null)
const AUTHENTICATED_FILE_SRC_ATTRIBUTE = 'data-authenticated-file-src'
const AUTHENTICATED_FILE_HREF_ATTRIBUTE = 'data-authenticated-file-href'
const LOCAL_FILE_PATH_PATTERN = /^\/api\/v1\/files\/([1-9]\d*)$/
let activeController: AbortController | null = null
let hydrationGeneration = 0
const objectUrls = new Set<string>()

const renderedHtml = computed(() => {
  if (typeof DOMParser === 'undefined') return props.html

  const document = new DOMParser().parseFromString(props.html, 'text/html')
  document.querySelectorAll<HTMLImageElement>('img[src]').forEach((image) => {
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
  })
  return asSanitizedHtml(document.body.innerHTML)
})

function resolveAuthenticatedFileRequestPath(src: string | null): string | null {
  if (!src || typeof window === 'undefined') return null

  try {
    const url = new URL(src, window.location.origin)
    if (url.origin !== window.location.origin) return null

    const match = url.pathname.match(LOCAL_FILE_PATH_PATTERN)
    return match ? `/files/${match[1]}${url.search}` : null
  } catch {
    return null
  }
}

function releaseAuthenticatedFiles() {
  hydrationGeneration += 1
  activeController?.abort()
  activeController = null
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.clear()
}

async function hydrateAuthenticatedFiles() {
  releaseAuthenticatedFiles()
  const generation = hydrationGeneration
  await nextTick()

  if (generation !== hydrationGeneration) return
  const root = element.value
  if (!root) return

  const controller = new AbortController()
  activeController = controller
  const images = Array.from(
    root.querySelectorAll<HTMLImageElement>(`img[${AUTHENTICATED_FILE_SRC_ATTRIBUTE}]`),
  )
  const links = Array.from(
    root.querySelectorAll<HTMLAnchorElement>(`a[${AUTHENTICATED_FILE_HREF_ATTRIBUTE}]`),
  )
  if (images.length === 0 && links.length === 0) {
    activeController = null
    return
  }

  const { default: api } = await import('@/api')
  if (controller.signal.aborted || generation !== hydrationGeneration) return

  const targets = [
    ...images.map((node) => ({ node, requestPath: node.getAttribute(AUTHENTICATED_FILE_SRC_ATTRIBUTE), kind: 'image' as const })),
    ...links.map((node) => ({ node, requestPath: node.getAttribute(AUTHENTICATED_FILE_HREF_ATTRIBUTE), kind: 'link' as const })),
  ]
  await Promise.allSettled(targets.map(async ({ node, requestPath, kind }) => {
    if (!requestPath) return

    try {
      const response = await api.get<Blob>(requestPath, {
        responseType: 'blob',
        signal: controller.signal,
        skipGlobalErrorHandler: true,
      })
      if (controller.signal.aborted || generation !== hydrationGeneration || !root.contains(node)) return

      const objectUrl = URL.createObjectURL(response.data)
      objectUrls.add(objectUrl)
      if (kind === 'image') {
        node.src = objectUrl
      } else {
        node.href = objectUrl
        node.removeAttribute('aria-disabled')
      }
    } catch {
      if (controller.signal.aborted || generation !== hydrationGeneration || !root.contains(node)) return
      if (kind === 'image' && props.useImageFallback) node.src = '/images/default-emoticon.png'
      if (kind === 'link') node.setAttribute('aria-disabled', 'true')
    }
  }))

  if (activeController === controller) activeController = null
}

watch(renderedHtml, () => {
  void hydrateAuthenticatedFiles()
}, { flush: 'post' })

onMounted(() => {
  void hydrateAuthenticatedFiles()
})

const stopSessionBoundary = subscribeAuthSessionBoundary(() => {
  void hydrateAuthenticatedFiles()
})

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
    @error.capture="useImageFallback ? applyImageFallback($event) : undefined"
  />
</template>
