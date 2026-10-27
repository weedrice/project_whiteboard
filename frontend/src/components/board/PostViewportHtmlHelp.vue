<script setup lang="ts">
import { onScopeDispose, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const { t } = useI18n()
const copyState = ref<'idle' | 'copied' | 'failed'>('idle')
let disposed = false
onScopeDispose(() => { disposed = true })

// Plain text only: opening or copying this example never inserts or runs it.
const example = `<template data-noviis-layer="viewport">
  <style>
    .sparkle {
      position: fixed;
      top: 15vh;
      left: 70vw;
      /* typography-guard-allow: standalone sandbox examples cannot inherit host typography tokens */
      font-size: 3rem;
      animation: float 3s ease-in-out infinite alternate;
    }
    @keyframes float {
      to { transform: translateY(30vh) rotate(90deg); }
    }
  </style>
  <span class="sparkle">✦</span>
</template>`

async function copyExample() {
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
    await navigator.clipboard.writeText(example)
    if (!disposed) copyState.value = 'copied'
  } catch {
    if (!disposed) copyState.value = 'failed'
  }
}
</script>

<template>
  <details class="viewport-html-help rounded-xl border border-[var(--nv-line)] bg-[var(--nv-elevated)] p-3 text-sm">
    <summary class="cursor-pointer rounded font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--nv-accent)]">
      {{ t('board.writePost.viewportHtmlHelp.title') }}
    </summary>
    <div class="mt-3 space-y-3">
      <p>{{ t('board.writePost.viewportHtmlHelp.description') }}</p>
      <p class="text-[var(--nv-ink-soft)]">{{ t('board.writePost.viewportHtmlHelp.images') }}</p>
      <pre class="max-h-64 overflow-auto rounded-lg bg-[var(--nv-surface)] p-3 text-xs" tabindex="0" :aria-label="t('board.writePost.viewportHtmlHelp.example')"><code>{{ example }}</code></pre>
      <div class="flex flex-wrap items-center gap-2">
        <BaseButton variant="secondary" size="sm" @click="copyExample">
          {{ t('board.writePost.viewportHtmlHelp.copy') }}
        </BaseButton>
        <span role="status">
          <template v-if="copyState === 'copied'">{{ t('board.writePost.viewportHtmlHelp.copied') }}</template>
          <template v-else-if="copyState === 'failed'">{{ t('board.writePost.viewportHtmlHelp.copyFailed') }}</template>
        </span>
      </div>
      <p class="text-[var(--nv-ink-soft)]">{{ t('board.writePost.viewportHtmlHelp.playback') }}</p>
    </div>
  </details>
</template>
