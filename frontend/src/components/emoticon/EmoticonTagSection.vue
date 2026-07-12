<script setup lang="ts">
import { X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import type { EmoticonTagItem } from '@/features/emoticon/form/useEmoticonTagItems'

defineProps<{
  inputId: string
  modelValue: string
  tagItems: EmoticonTagItem[]
  tagCount: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  add: []
  remove: [clientId: string]
}>()

const { t } = useI18n()
</script>

<template>
  <BaseCard padding="lg" bordered>
    <div class="mb-2 flex items-center gap-2">
      <span class="text-sm font-medium nv-text-muted">{{ t('emoticon.tag.label') }}</span>
      <span class="text-xs font-normal nv-text-subtle">({{ t('emoticon.tag.count', { count: tagCount }) }})</span>
    </div>
    <p class="text-xs nv-text-subtle mb-4">{{ t('emoticon.tag.help') }}</p>

    <div class="flex gap-2 mb-4">
      <BaseInput
        :id="inputId"
        :model-value="modelValue"
        name="emoticonTag"
        autocomplete="off"
        type="text"
        :label="t('emoticon.tag.label')"
        hide-label
        :placeholder="t('emoticon.tag.placeholder')"
        class="flex-1"
        input-class="rounded-lg px-4"
        @update:model-value="emit('update:modelValue', String($event))"
        @keydown.enter.prevent="emit('add')"
      />
      <BaseButton type="button" @click="emit('add')" variant="secondary">
        {{ t('common.add') }}
      </BaseButton>
    </div>

    <div v-if="tagCount > 0" class="flex flex-wrap gap-2">
      <span
        v-for="tagItem in tagItems"
        :key="tagItem.clientId"
        class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium nv-status-info"
      >
        #{{ tagItem.value }}
        <button
          type="button"
          :aria-label="t('board.tags.remove')"
          :title="t('board.tags.remove')"
          class="ml-1 nv-accent-text hover:brightness-95"
          @click="emit('remove', tagItem.clientId)"
        >
          <X class="w-3 h-3" />
        </button>
      </span>
    </div>
  </BaseCard>
</template>
