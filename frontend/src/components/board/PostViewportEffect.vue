<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { getCurrentQuerySessionSource, subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import { buildViewportHtmlSource } from '@/utils/postViewportHtml'
import { extractViewportHtml, hydrateViewportImages, VIEWPORT_EFFECT_DURATION, ViewportImageValidationError, type ViewportValidationReason } from '@/utils/postViewportEffect'

const props = withDefaults(defineProps<{
  content: string
  enabled?: boolean
  preview?: boolean
}>(), { enabled: true, preview: false })

const { t } = useI18n()
const effect = computed<{ html: string | null; invalid: boolean; reason?: ViewportValidationReason }>(() => (
  props.enabled ? extractViewportHtml(props.content) : { html: null, invalid: false }
))
const srcdoc = ref<string | null>(null)
const loading = ref(false)
const failure = ref<ViewportValidationReason | 'network' | 'invalid' | null>(null)
const diagnosticMessage = computed(() => {
  const reason = effect.value.invalid ? effect.value.reason ?? 'invalid' : failure.value
  switch (reason) {
    case null: return null
    case 'duplicateLayers': return t('board.viewportEffect.duplicateLayers')
    case 'htmlTooLarge': return t('board.viewportEffect.htmlTooLarge')
    case 'tooManyElements': return t('board.viewportEffect.tooManyElements')
    case 'cssResource': return t('board.viewportEffect.cssResource')
    case 'tooManyImages': return t('board.viewportEffect.tooManyImages')
    case 'imageSource': return t('board.viewportEffect.imageSource')
    case 'imageType': return t('board.viewportEffect.imageType')
    case 'imageBytes': return t('board.viewportEffect.imageBytes')
    case 'imageDataTooLarge': return t('board.viewportEffect.imageDataTooLarge')
    case 'network': return t('board.viewportEffect.failed')
    default: return t('board.viewportEffect.invalid')
  }
})
const reducedMotion = ref(true)
let motionQuery: MediaQueryList | null = null
let scope: AuthSessionScope | null = null
let revision = 0
let timer: ReturnType<typeof setTimeout> | undefined

function stop() {
  revision += 1
  srcdoc.value = null
  loading.value = false
  clearTimeout(timer)
  scope?.cancel()
  scope?.dispose()
  scope = null
}

async function play() {
  stop()
  failure.value = null
  if (!props.enabled || reducedMotion.value || document.hidden || effect.value.html === null) return
  const currentRevision = revision
  const operation = createAuthSessionScope(getCurrentQuerySessionSource())
  scope = operation
  const isCurrent = () => revision === currentRevision && operation.isCurrent()
  loading.value = true
  timer = setTimeout(stop, VIEWPORT_EFFECT_DURATION)
  try {
    const html = effect.value.html
    const result = await hydrateViewportImages(html, async (path) => {
      const { default: api } = await import('@/api')
      operation.assertCurrent()
      if (!isCurrent()) throw new Error('Stale viewport effect')
      const response = await api.get<Blob>(path, operation.withRequestConfig({
        responseType: 'blob', skipGlobalErrorHandler: true,
      }))
      return response.data
    }, isCurrent)
    if (!isCurrent()) return
    const source = buildViewportHtmlSource(result.html, result.imageSources)
    if (!source) {
      stop()
      failure.value = 'invalid'
      return
    }
    srcdoc.value = source
  } catch (error) {
    if (isCurrent()) {
      stop()
      failure.value = error instanceof ViewportImageValidationError ? error.reason : 'network'
    }
  } finally {
    if (revision === currentRevision) loading.value = false
  }
}

function onMotionChange() {
  reducedMotion.value = motionQuery?.matches ?? false
  if (reducedMotion.value) stop()
}
function onVisibilityChange() { if (document.hidden) stop() }
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape') stop() }
const unsubscribe = subscribeAuthSessionBoundary(stop)
watch(() => [props.content, props.enabled, props.preview], () => { stop(); failure.value = null }, { flush: 'sync' })
onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  onMotionChange()
  motionQuery.addEventListener('change', onMotionChange)
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  stop()
  unsubscribe()
  motionQuery?.removeEventListener('change', onMotionChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <section v-if="effect.html !== null || effect.invalid" class="viewport-effect-panel rounded-lg border nv-border nv-surface p-3" data-testid="viewport-effect-panel">
    <p class="text-sm font-semibold nv-title">{{ preview ? t('board.viewportEffect.preview') : t('board.viewportEffect.title') }}</p>
    <p class="mt-1 text-sm nv-text-subtle">{{ t('board.viewportEffect.description') }}</p>
    <p v-if="diagnosticMessage" role="status" class="mt-2 text-sm">{{ diagnosticMessage }}</p>
    <p v-else-if="reducedMotion" role="status" class="mt-2 text-sm">{{ t('board.viewportEffect.reducedMotion') }}</p>
    <div class="mt-2 flex gap-2">
      <BaseButton v-if="!srcdoc && !loading" variant="secondary" size="sm" :disabled="effect.invalid || reducedMotion" @click="play">{{ t('board.viewportEffect.play') }}</BaseButton>
      <BaseButton v-else variant="secondary" size="sm" @click="stop">{{ t('board.viewportEffect.stop') }}</BaseButton>
    </div>
    <iframe v-if="preview && srcdoc" :srcdoc="srcdoc" :title="t('board.viewportEffect.preview')" sandbox="" inert tabindex="-1" aria-hidden="true" referrerpolicy="no-referrer" class="viewport-effect-preview" />
    <Teleport v-if="!preview && srcdoc" to="body">
      <iframe :srcdoc="srcdoc" :title="t('board.viewportEffect.title')" sandbox="" inert tabindex="-1" aria-hidden="true" referrerpolicy="no-referrer" class="viewport-effect-frame" data-testid="viewport-effect-frame" />
      <div class="viewport-effect-stop">
        <BaseButton variant="secondary" size="sm" @click="stop">{{ t('board.viewportEffect.stop') }}</BaseButton>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.viewport-effect-frame, .viewport-effect-preview {
  pointer-events: none;
  border: 0;
  background: transparent;
  opacity: 0.7;
}
.viewport-effect-frame {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: calc(var(--nv-z-sticky, 60) - 2);
}
.viewport-effect-stop {
  position: fixed;
  right: 1rem;
  bottom: calc(5rem + env(safe-area-inset-bottom));
  z-index: calc(var(--nv-z-sticky, 60) - 1);
}
.viewport-effect-preview { display: block; width: 100%; height: 320px; margin-top: 0.75rem; }
@media (prefers-reduced-motion: reduce) {
  .viewport-effect-frame, .viewport-effect-preview { display: none; }
}
</style>
