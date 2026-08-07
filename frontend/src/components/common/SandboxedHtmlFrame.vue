<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
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
const frameId = `post-html-${useId()}`
const height = ref(props.minHeight)
const srcdoc = computed(() => buildSandboxedPostHtmlSource(props.html, frameId))

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
    || data.type !== 'noviis-post-html-height'
    || data.channel !== 'noviis-post-html-sandbox'
    || data.id !== frameId
  ) return

  const nextHeight = Math.ceil(Number(data.height))
  if (!Number.isFinite(nextHeight)) return

  height.value = Math.max(props.minHeight, Math.min(nextHeight, 4000))
}

onMounted(() => {
  window.addEventListener('message', onMessage)
})

onBeforeUnmount(() => {
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
