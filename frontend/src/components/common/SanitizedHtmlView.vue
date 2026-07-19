<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import api from '@/api'
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

  await Promise.allSettled(images.map(async (image) => {
    const requestPath = image.getAttribute(AUTHENTICATED_FILE_SRC_ATTRIBUTE)
    if (!requestPath) return

    try {
      const response = await api.get<Blob>(requestPath, {
        responseType: 'blob',
        signal: controller.signal,
        skipGlobalErrorHandler: true,
      })
      if (controller.signal.aborted || generation !== hydrationGeneration || !root.contains(image)) return

      const objectUrl = URL.createObjectURL(response.data)
      objectUrls.add(objectUrl)
      image.src = objectUrl
    } catch {
      if (controller.signal.aborted || generation !== hydrationGeneration || !root.contains(image)) return
      if (props.useImageFallback) image.src = '/images/default-emoticon.png'
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
