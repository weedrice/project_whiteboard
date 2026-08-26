<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import api from '@/api'
import type { InquiryAttachment } from '@/types/inquiry'
import { resolveAuthenticatedFileRequestPath } from '@/utils/authenticatedFile'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ attachment: InquiryAttachment }>()
const { t } = useI18n()
const objectUrl = ref<string | null>(null)
let controller: AbortController | null = null

function release() {
  controller?.abort()
  controller = null
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = null
}

async function load() {
  release()
  const path = resolveAuthenticatedFileRequestPath(props.attachment.url)
  if (!path) return
  const current = new AbortController()
  controller = current
  try {
    const response = await api.get<Blob>(path, {
      responseType: 'blob',
      signal: current.signal,
      skipGlobalErrorHandler: true,
    })
    if (!current.signal.aborted) objectUrl.value = URL.createObjectURL(response.data)
  } catch {
    // Keep the inaccessible image hidden; the surrounding inquiry remains readable.
  } finally {
    if (controller === current) controller = null
  }
}

watch(() => props.attachment.url, load, { immediate: true })
onBeforeUnmount(release)
</script>

<template>
  <a v-if="objectUrl" :href="objectUrl" target="_blank" rel="noopener" class="block overflow-hidden rounded-lg border nv-border">
    <img :src="objectUrl" :alt="attachment.originalName" class="aspect-video h-full w-full object-cover" loading="lazy">
  </a>
  <div v-else class="flex aspect-video items-center justify-center rounded-lg border nv-border text-xs nv-text-muted">{{ t('inquiry.common.image') }}</div>
</template>
