<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { resolveAuthenticatedFileRequestPath } from '@/utils/authenticatedFile'
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
let activeController: AbortController | null = null
let hydrationGeneration = 0
const objectUrls = new Set<string>()
const linkObjectUrls = new Map<HTMLAnchorElement, string>()
const linkControllers = new Set<AbortController>()

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
    link.setAttribute('role', 'link')
    link.setAttribute('tabindex', '0')
  })
  return asSanitizedHtml(document.body.innerHTML)
})

function releaseAuthenticatedFiles() {
  hydrationGeneration += 1
  activeController?.abort()
  activeController = null
  linkControllers.forEach((controller) => controller.abort())
  linkControllers.clear()
  linkObjectUrls.clear()
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
  if (images.length === 0) {
    activeController = null
    return
  }

  const { default: api } = await import('@/api')
  if (controller.signal.aborted || generation !== hydrationGeneration) return

  await Promise.allSettled(images.map(async (node) => {
    const requestPath = node.getAttribute(AUTHENTICATED_FILE_SRC_ATTRIBUTE)
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
      node.src = objectUrl
    } catch {
      if (controller.signal.aborted || generation !== hydrationGeneration || !root.contains(node)) return
      if (props.useImageFallback) node.src = '/images/default-emoticon.png'
    }
  }))

  if (activeController === controller) activeController = null
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
  const controller = new AbortController()
  linkControllers.add(controller)
  link.setAttribute('aria-busy', 'true')
  link.removeAttribute('aria-disabled')

  try {
    const { default: api } = await import('@/api')
    const response = await api.get<Blob>(requestPath, {
      responseType: 'blob',
      signal: controller.signal,
      skipGlobalErrorHandler: true,
    })
    if (controller.signal.aborted || generation !== hydrationGeneration || !root.contains(link)) return

    const objectUrl = URL.createObjectURL(response.data)
    objectUrls.add(objectUrl)
    linkObjectUrls.set(link, objectUrl)
    followAuthenticatedFileLink(link, objectUrl, event)
  } catch {
    if (!controller.signal.aborted && generation === hydrationGeneration && root.contains(link)) {
      link.setAttribute('aria-disabled', 'true')
    }
  } finally {
    linkControllers.delete(controller)
    if (root.contains(link)) link.removeAttribute('aria-busy')
  }
}

function followAuthenticatedFileLink(
  source: HTMLAnchorElement,
  objectUrl: string,
  event: MouseEvent | KeyboardEvent,
) {
  const link = document.createElement('a')
  link.href = objectUrl
  const requestedTarget = source.getAttribute('target')
  const openInNewTab = event instanceof MouseEvent && (event.ctrlKey || event.metaKey || event.shiftKey)
  if (requestedTarget) link.target = requestedTarget
  if (openInNewTab) link.target = '_blank'
  const rel = source.getAttribute('rel')
  if (rel) link.rel = rel
  link.click()
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
    @click="activateAuthenticatedFile"
    @keydown.enter="activateAuthenticatedFile"
    @error.capture="useImageFallback ? applyImageFallback($event) : undefined"
  />
</template>
