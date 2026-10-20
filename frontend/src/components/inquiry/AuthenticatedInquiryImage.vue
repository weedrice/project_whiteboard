<script setup lang="ts">
import { vImageState } from '@/directives/imageState'
import { onBeforeUnmount, ref, watch } from 'vue'
import api from '@/api'
import type { InquiryAttachment } from '@/types/inquiry'
import { resolveAuthenticatedFileRequestPath } from '@/utils/authenticatedFile'
import { useAuthStore } from '@/stores/auth'
import { createAuthSessionScope } from '@/utils/authSessionScope'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'

const props = defineProps<{ attachment: InquiryAttachment }>()
const authStore = useAuthStore()
const objectUrl = ref<string | null>(null)
const pending = ref(false)
const failed = ref(false)
let controller: AbortController | null = null

function release() {
  controller?.abort()
  controller = null
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = null
  pending.value = false
  failed.value = true
}

async function load() {
  release()
  const path = resolveAuthenticatedFileRequestPath(props.attachment.url)
  if (!path) return
  pending.value = true
  failed.value = false
  const scope = createAuthSessionScope(authStore)
  const current = new AbortController()
  controller = current
  try {
    const response = await api.get<Blob>(path, scope.withRequestConfig({
      responseType: 'blob',
      signal: current.signal,
      skipGlobalErrorHandler: true,
    }))
    if (!current.signal.aborted && scope.isCurrent()) objectUrl.value = URL.createObjectURL(response.data)
  } catch {
    if (!current.signal.aborted && scope.isCurrent()) failed.value = true
  } finally {
    scope.dispose()
    if (controller === current) {
      controller = null
      pending.value = false
    }
  }
}

watch(() => props.attachment.url, load, { immediate: true })
useAuthSessionReset(release)
onBeforeUnmount(release)
</script>

<template>
  <a :href="objectUrl ?? undefined" target="_blank" rel="noopener" class="block overflow-hidden rounded-lg border nv-border">
    <img v-image-state="{ pending, failed }" :src="objectUrl ?? undefined" :alt="attachment.originalName" class="aspect-video h-full w-full object-cover" loading="lazy">
  </a>
</template>
