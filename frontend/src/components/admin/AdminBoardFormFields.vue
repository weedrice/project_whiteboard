<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import { BOARD_WRITE_LIMITS } from '@/utils/board'

const props = withDefaults(defineProps<{
  boardName: string
  boardUrl: string
  description?: string
  agentUseYn?: boolean
  guidePrompt?: string
  agentDisabled?: boolean
  boardUrlPattern?: string
  layout?: 'grid' | 'stack'
}>(), {
  agentDisabled: false,
  boardUrlPattern: undefined,
  layout: 'stack',
})

const emit = defineEmits<{
  (event: 'update:boardName', value: string): void
  (event: 'update:boardUrl', value: string): void
  (event: 'update:description', value: string): void
  (event: 'update:agentUseYn', value: boolean): void
  (event: 'update:guidePrompt', value: string): void
}>()

const { t } = useI18n()

const identityClass = computed(() => (
  props.layout === 'grid'
    ? 'grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-12'
    : 'space-y-4'
))
const nameFieldClass = computed(() => (props.layout === 'grid' ? 'md:col-span-4' : ''))
const urlFieldClass = computed(() => (props.layout === 'grid' ? 'md:col-span-4' : ''))
</script>

<template>
  <div :class="identityClass">
    <div :class="nameFieldClass">
      <BaseInput
        :model-value="boardName"
        :label="t('board.form.name')"
        :placeholder="t('board.form.placeholder.name')"
        :maxlength="BOARD_WRITE_LIMITS.boardName"
        @update:model-value="emit('update:boardName', String($event))"
      />
    </div>
    <div :class="urlFieldClass">
      <BaseInput
        :model-value="boardUrl"
        :label="t('board.form.url')"
        :placeholder="t('board.form.placeholder.url')"
        :pattern="boardUrlPattern"
        :maxlength="BOARD_WRITE_LIMITS.boardUrl"
        @update:model-value="emit('update:boardUrl', String($event))"
      />
    </div>
    <slot name="after-identity" />
  </div>

  <BaseTextarea
    :model-value="description ?? ''"
    :label="t('board.form.description')"
    :placeholder="t('board.form.placeholder.desc')"
    rows="4"
    :maxlength="BOARD_WRITE_LIMITS.description"
    @update:model-value="emit('update:description', String($event))"
  />

  <BaseCheckbox
    :model-value="Boolean(agentUseYn)"
    :label="t('board.form.agentUseYn')"
    :description="t('board.form.agentUseYnDesc')"
    :disabled="agentDisabled"
    @update:model-value="emit('update:agentUseYn', Boolean($event))"
  />

  <BaseTextarea
    v-if="agentUseYn"
    :model-value="guidePrompt ?? ''"
    :label="t('board.form.guidePrompt')"
    :placeholder="t('board.form.placeholder.guidePrompt')"
    rows="6"
    :maxlength="BOARD_WRITE_LIMITS.guidePrompt"
    @update:model-value="emit('update:guidePrompt', String($event))"
  />
</template>
