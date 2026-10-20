<script setup lang="ts">
import { vImageState } from '@/directives/imageState'
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import api from '@/api'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { InquiryAttachment } from '@/types/inquiry'
import { resolveAuthenticatedFileRequestPath } from '@/utils/authenticatedFile'
import { useAuthStore } from '@/stores/auth'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'

const props = defineProps<{ attachment: InquiryAttachment }>()
const authStore = useAuthStore()
const { t } = useI18n()
const objectUrl = ref<string | null>(null)
const pending = ref(false)
const failed = ref(false)
const retrying = ref(false)
const imageGeneration = ref(0)
const imageLinkRef = ref<HTMLAnchorElement | null>(null)
const retryButtonRef = ref<InstanceType<typeof BaseButton> | null>(null)
let controller: AbortController | null = null
let activeScope: AuthSessionScope | null = null

function release() {
  imageGeneration.value += 1
  controller?.abort()
  controller = null
  activeScope?.cancel()
  activeScope?.dispose()
  activeScope = null
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = null
  pending.value = false
  failed.value = true
  retrying.value = false
}

async function load(isRetry = false) {
  release()
  const path = resolveAuthenticatedFileRequestPath(props.attachment.url)
  if (!path) return
  pending.value = true
  failed.value = false
  retrying.value = isRetry
  const scope = createAuthSessionScope(authStore)
  activeScope = scope
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
    if (activeScope === scope) activeScope = null
    if (controller === current) {
      controller = null
      pending.value = false
      if (failed.value) retrying.value = false
    }
  }
}

function handleImageResult(event: Event) {
  const image = event.target
  if (!(image instanceof HTMLImageElement)
    || image.dataset.requestGeneration !== String(imageGeneration.value)
    || pending.value || !objectUrl.value || image.getAttribute('src') !== objectUrl.value) return
  const state = event.type === 'error' ? 'error'
    : event.type === 'load' && image.naturalWidth > 0 ? 'loaded' : image.dataset.imageState
  if (state !== 'loaded' && state !== 'error') return
  if (state === 'loaded' && retrying.value && retryButtonRef.value?.$el === document.activeElement) {
    imageLinkRef.value?.focus({ preventScroll: true })
  }
  failed.value = state === 'error'
  retrying.value = false
}

function retryImage() {
  if (pending.value || retrying.value || !failed.value) return
  void load(true)
}

watch(() => props.attachment.url, () => { void load() }, { immediate: true, flush: 'sync' })
useAuthSessionReset(release)
onBeforeUnmount(release)
</script>

<template>
  <div class="space-y-2">
    <a ref="imageLinkRef" :href="failed ? undefined : objectUrl ?? undefined" target="_blank" rel="noopener" class="block overflow-hidden rounded-lg border nv-border">
      <img
        :key="imageGeneration"
        v-image-state="{ pending, failed }"
        :data-request-generation="imageGeneration"
        :src="objectUrl ?? undefined"
        :alt="attachment.originalName"
        class="aspect-video h-full w-full object-cover"
        loading="lazy"
        @load="handleImageResult"
        @error="handleImageResult"
        @imagestatechange="handleImageResult"
      >
    </a>
    <BaseButton
      v-if="failed || retrying"
      ref="retryButtonRef"
      size="sm"
      variant="secondary"
      :aria-disabled="retrying || undefined"
      :aria-busy="retrying || undefined"
      @click="retryImage"
    >
      {{ retrying ? t('common.imageRetrying') : t('common.imageRetry') }}
    </BaseButton>
  </div>
</template>
