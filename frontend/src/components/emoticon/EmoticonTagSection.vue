<script setup lang="ts">
import { X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { EmoticonTagItem } from '@/composables/useEmoticonTagItems'

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
  <div class="nv-surface rounded-lg shadow-sm border nv-border p-6">
    <label :for="inputId" class="block text-sm font-medium nv-text-muted mb-2">
      {{ t('emoticon.tag.label') }}
      <span class="text-xs font-normal nv-text-subtle ml-2">({{ t('emoticon.tag.count', { count: tagCount }) }})</span>
    </label>
    <p class="text-xs nv-text-subtle mb-4">{{ t('emoticon.tag.help') }}</p>

    <div class="flex gap-2 mb-4">
      <input
        :id="inputId"
        :value="modelValue"
        name="emoticonTag"
        autocomplete="off"
        type="text"
        :placeholder="t('emoticon.tag.placeholder')"
        class="flex-1 px-4 py-2 border nv-border rounded-lg nv-surface nv-title placeholder:text-[var(--nv-text-subtle)] focus:ring-2 focus:ring-[var(--nv-focus)] focus:border-transparent"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
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
  </div>
</template>
