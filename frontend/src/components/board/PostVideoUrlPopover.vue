<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'

defineProps<{
  show: boolean
  isTopDialog: boolean
  modelValue: string
  popoverStyle: { top: string; left: string }
  assignPopoverRef: (value: Element | ComponentPublicInstance | null) => void
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'close'): void
  (event: 'submit'): void
}>()
</script>

<template>
  <Teleport to="body">
    <div
      v-if="show"
      class="video-url-popover-mask nv-dialog-overlay nv-dialog-overlay--popover"
      @click.self="emit('close')"
      @keydown.enter.stop
    >
      <div
        :ref="assignPopoverRef"
        class="video-url-popover nv-dialog-surface nv-dialog-surface--popover"
        :style="{ top: popoverStyle.top, left: popoverStyle.left }"
        role="dialog"
        :aria-modal="isTopDialog ? 'true' : undefined"
        :aria-hidden="isTopDialog ? undefined : 'true'"
        :inert="isTopDialog ? undefined : true"
        :aria-label="$t('board.writePost.video.inputLabel')"
      >
        <BaseInput
          id="post-video-url-input"
          :model-value="modelValue"
          type="url"
          :label="$t('board.writePost.video.inputLabel')"
          label-class="video-url-popover-label"
          input-class="video-url-popover-input"
          :placeholder="$t('board.writePost.video.placeholder')"
          aria-describedby="post-video-url-help"
          @update:model-value="emit('update:modelValue', String($event))"
          @keydown.enter.stop.prevent="emit('submit')"
        />
        <p id="post-video-url-help" class="video-url-popover-help">
          {{ $t('board.writePost.video.help') }}
        </p>
        <div class="video-url-popover-actions nv-dialog-actions">
          <BaseButton type="button" variant="secondary" size="sm" @click="emit('close')">
            {{ $t('common.cancel') }}
          </BaseButton>
          <BaseButton type="button" variant="primary" size="sm" @click="emit('submit')">
            {{ $t('common.confirm') }}
          </BaseButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style>
.video-url-popover-mask {
  z-index: var(--nv-z-overlay);
}

.video-url-popover {
  position: fixed;
  transform: translateX(-50%);
  width: min(420px, calc(100vw - 24px));
  min-width: 0;
  max-height: calc(100dvh - 24px);
  overflow-y: auto;
  padding: 12px 14px;
  z-index: var(--nv-z-popup);
}

.video-url-popover-label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--nv-ink-soft);
}

.video-url-popover-input {
  display: block;
  width: 100%;
  margin-bottom: 6px;
  padding: 10px 12px;
  border: 1px solid var(--nv-line);
  border-radius: 8px;
  background: var(--nv-elevated);
  color: var(--nv-ink);
  box-sizing: border-box;
}

.video-url-popover-help {
  margin: 0 0 10px;
  color: var(--nv-muted);
  font-size: 12px;
}

.video-url-popover-actions {
  margin-top: 10px;
}
</style>
