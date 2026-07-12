<template>
  <div class="space-y-2">
    <p class="text-xs sm:text-sm font-medium nv-text-muted">
      {{ t('board.form.iconUrl') }}
    </p>
    <div class="flex items-start gap-4">
      <div class="h-20 w-20 flex-shrink-0 overflow-hidden rounded-full border nv-border nv-surface-muted flex items-center justify-center">
        <img
          v-if="iconUrl"
          :src="iconUrl"
          alt=""
          class="h-full w-full object-contain"
        />
        <span v-else class="text-xs nv-text-subtle">{{ t('admin.boards.iconEmpty') }}</span>
      </div>

      <div class="flex-1 min-w-0 space-y-2 pt-1">
        <p class="break-all text-sm leading-5" :class="iconUrl ? 'nv-text-muted' : 'nv-text-subtle'">
          {{ iconUrl || t('admin.boards.iconUrlEmpty') }}
        </p>

        <div class="flex items-center">
          <input
            type="file"
            :ref="setFileInputRef"
            :accept="BOARD_ICON_UPLOAD_POLICY.accept"
            class="hidden"
            @change="emit('upload', $event)"
          />
          <BaseButton type="button" variant="secondary" size="sm" @click="emit('choose')">
            {{ t('admin.boards.chooseFile') }}
          </BaseButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { BOARD_ICON_UPLOAD_POLICY } from '@/utils/imageUploadPolicy'

defineProps<{
  iconUrl: string
  setFileInputRef: (element: Element | ComponentPublicInstance | null) => void
}>()

const emit = defineEmits<{
  (event: 'upload', uploadEvent: Event): void
  (event: 'choose'): void
}>()

const { t } = useI18n()
</script>
