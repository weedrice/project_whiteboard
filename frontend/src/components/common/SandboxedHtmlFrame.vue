<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { buildSandboxedPostHtmlSource } from '@/utils/postHtmlSandbox'

const props = withDefaults(defineProps<{
  html: string
  title?: string
  minHeight?: number
  loading?: 'eager' | 'lazy'
}>(), {
  minHeight: 240,
  loading: 'lazy',
})

const frame = ref<HTMLIFrameElement | null>(null)
const { t } = useI18n()
const baseFrameId = `post-html-${useId()}`
const documentRevision = ref(0)
const staticFallback = ref(false)
const frameId = computed(() => `${baseFrameId}-${documentRevision.value}-${staticFallback.value ? 'static' : 'managed'}`)
const errorLabel = computed(() => t('common.imageLoadError'))
const height = ref(props.minHeight)
const srcdoc = computed(() => buildSandboxedPostHtmlSource(props.html, frameId.value, errorLabel.value, {
  staticFallback: staticFallback.value,
}))
let bridgeReady = false
let frameVisible = false
let visibilityObserver: IntersectionObserver | null = null
let bridgeTimer: ReturnType<typeof setTimeout> | null = null

function clearBridgeTimer() {
  if (bridgeTimer !== null) clearTimeout(bridgeTimer)
  bridgeTimer = null
}

function scheduleBridgeFallback(delay: number) {
  clearBridgeTimer()
  if (staticFallback.value || bridgeReady) return
  const loadedFrameId = frameId.value
  // The opaque sandbox cannot be inspected by its parent. Restore a static
  // document if its trusted bridge never confirms initialization.
  bridgeTimer = setTimeout(() => {
    bridgeTimer = null
    if (!bridgeReady && frameId.value === loadedFrameId) staticFallback.value = true
  }, delay)
}

function onFrameLoad() {
  scheduleBridgeFallback(1000)
}

watch(() => [props.html, errorLabel.value], () => {
  clearBridgeTimer()
  documentRevision.value += 1
  bridgeReady = false
  staticFallback.value = false
  if (frameVisible) scheduleBridgeFallback(10_000)
})

watch(
  () => [props.html, props.minHeight] as const,
  () => {
    height.value = props.minHeight
  },
)

function onMessage(event: MessageEvent) {
  if (event.source !== frame.value?.contentWindow) return

  const data = event.data
  if (
    !data
    || data.channel !== 'noviis-post-html-sandbox'
    || data.id !== frameId.value
  ) return

  if (data.type === 'noviis-post-html-ready') {
    bridgeReady = true
    clearBridgeTimer()
    return
  }
  if (data.type !== 'noviis-post-html-height') return

  const nextHeight = Math.ceil(Number(data.height))
  if (!Number.isFinite(nextHeight)) return

  bridgeReady = true
  clearBridgeTimer()
  height.value = Math.max(props.minHeight, Math.min(nextHeight, 4000))
}

onMounted(() => {
  window.addEventListener('message', onMessage)
  if (frame.value && typeof IntersectionObserver === 'function') {
    visibilityObserver = new IntersectionObserver(([entry]) => {
      frameVisible = Boolean(entry?.isIntersecting)
      // Do not time out a lazy iframe before it enters the viewport. This
      // watchdog also covers stalled eager images that prevent iframe load.
      if (frameVisible && bridgeTimer === null) scheduleBridgeFallback(10_000)
    })
    visibilityObserver.observe(frame.value)
  }
})

onBeforeUnmount(() => {
  visibilityObserver?.disconnect()
  clearBridgeTimer()
  window.removeEventListener('message', onMessage)
})
</script>

<template>
  <iframe
    ref="frame"
    class="nv-sandboxed-html-frame"
    :title="title"
    :srcdoc="srcdoc"
    :style="{ height: `${height}px` }"
    sandbox="allow-scripts"
    referrerpolicy="no-referrer"
    :loading="loading"
    @load="onFrameLoad"
  />
</template>

<style scoped>
.nv-sandboxed-html-frame {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
}
</style>
