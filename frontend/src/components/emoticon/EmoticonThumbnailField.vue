<script setup lang="ts">
import { ref } from 'vue'
import { Upload, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

withDefaults(defineProps<{
  inputId: string
  inputName?: string
  accept: string
  preview: string | null
  mode?: 'create' | 'edit'
}>(), {
  inputName: 'thumbnailImage',
  mode: 'create',
})

const emit = defineEmits<{
  change: [event: Event]
  remove: []
}>()

const { t } = useI18n()
const fileInput = ref<HTMLInputElement | null>(null)

function openFileInput() {
  fileInput.value?.click()
}
</script>

<template>
  <div class="order-2 md:order-1 shrink-0">
    <label :for="inputId" class="block text-sm font-medium nv-text-muted mb-2">
      {{ t('emoticon.form.thumbnailImage') }} <span class="nv-form-error">*</span>
    </label>
    <p class="text-xs nv-text-subtle mb-4">{{ t('emoticon.form.thumbnailHelp') }}</p>
    <input
      :id="inputId"
      ref="fileInput"
      type="file"
      :name="inputName"
      :accept="accept"
      class="hidden"
      @change="emit('change', $event)"
    />

    <div v-if="preview" class="relative inline-block">
      <img
        :src="preview"
        :alt="t('emoticon.form.thumbnailPreview')"
        class="w-32 h-32 object-contain nv-surface-muted rounded-lg"
        :class="mode === 'edit' ? 'cursor-pointer hover:opacity-80 transition-opacity' : ''"
        :title="mode === 'edit' ? t('emoticon.form.changeImageTitle') : undefined"
        @click="mode === 'edit' && openFileInput()"
      />
      <button
        v-if="mode === 'create'"
        type="button"
        :aria-label="$t('common.delete')"
        :title="$t('common.delete')"
        class="absolute -right-3 -top-3 flex h-11 w-11 items-center justify-center rounded-full bg-[var(--nv-danger)] text-white hover:brightness-95 sm:-right-2 sm:-top-2 sm:h-6 sm:w-6"
        @click="emit('remove')"
      >
        <X class="w-4 h-4" />
      </button>
      <button
        v-else
        type="button"
        :aria-label="$t('common.edit')"
        :title="$t('common.edit')"
        class="absolute -bottom-3 -right-3 flex h-11 w-11 items-center justify-center rounded-full bg-[var(--nv-accent)] text-white shadow-md hover:brightness-95 sm:-bottom-2 sm:-right-2 sm:h-8 sm:w-8"
        @click="openFileInput"
      >
        <Upload class="w-4 h-4" />
      </button>
    </div>
    <div v-else-if="mode === 'edit'" class="relative inline-block">
      <div class="w-32 h-32 nv-surface-muted rounded-lg flex items-center justify-center nv-text-subtle">
        {{ t('common.noData') }}
      </div>
      <button
        type="button"
        :aria-label="$t('common.edit')"
        :title="$t('common.edit')"
        class="absolute -bottom-3 -right-3 flex h-11 w-11 items-center justify-center rounded-full bg-[var(--nv-accent)] text-white shadow-md hover:brightness-95 sm:-bottom-2 sm:-right-2 sm:h-8 sm:w-8"
        @click="openFileInput"
      >
        <Upload class="w-4 h-4" />
      </button>
    </div>
    <button
      v-else
      type="button"
      class="w-32 h-32 border-2 border-dashed nv-border rounded-lg flex flex-col items-center justify-center nv-text-subtle hover:border-[var(--nv-focus)] hover:text-[var(--nv-accent)] transition-colors"
      @click="openFileInput"
    >
      <Upload class="w-8 h-8 mb-2" />
      <span class="text-xs">{{ t('emoticon.form.chooseImage') }}</span>
    </button>
  </div>
</template>
