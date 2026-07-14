<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import EmoticonImageTile from '@/components/emoticon/EmoticonImageTile.vue'
import type { EmoticonImage } from '@/types/emoticon'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'

withDefaults(defineProps<{
  inputId: string
  inputName?: string
  accept: string
  currentCount: number
  maxCount?: number
  newImages: EmoticonImagePreview[]
  existingImages?: EmoticonImage[]
  imagesToDelete?: number[]
  allowAdd?: boolean
}>(), {
  inputName: 'emoticonImages',
  maxCount: 20,
  existingImages: () => [],
  imagesToDelete: () => [],
  allowAdd: true,
})

const emit = defineEmits<{
  select: [event: Event]
  removeNew: [clientId: string]
  markDelete: [imageId: number]
  unmarkDelete: [imageId: number]
}>()

const { t } = useI18n()
const fileInput = ref<HTMLInputElement | null>(null)

function openFileInput() {
  fileInput.value?.click()
}
</script>

<template>
  <div class="nv-surface rounded-lg shadow-sm border nv-border p-6">
    <label :for="inputId" class="block text-sm font-medium nv-text-muted mb-2">
      {{ t('emoticon.form.image') }} <span class="nv-form-error">*</span>
      <span class="text-xs font-normal nv-text-subtle ml-2">
        ({{ t('emoticon.form.count', { current: currentCount, total: maxCount }) }})
      </span>
    </label>
    <p class="text-xs nv-text-subtle mb-4">
      {{ t('emoticon.form.imageHelp', { count: maxCount }) }}
    </p>

    <div class="emoticon-image-grid mb-4 grid gap-2">
      <EmoticonImageTile
        v-for="image in existingImages"
        :key="'existing-' + image.imageId"
        :src="image.imageUrl"
        :alt="t('emoticon.form.imageAlt', { index: image.sortOrder + 1 })"
        :muted="imagesToDelete.includes(image.imageId)"
        :action="imagesToDelete.includes(image.imageId) ? 'cancel' : 'delete'"
        :action-label="imagesToDelete.includes(image.imageId) ? $t('common.cancel') : $t('common.delete')"
        :action-title="imagesToDelete.includes(image.imageId) ? $t('common.cancel') : $t('common.delete')"
        @action="imagesToDelete.includes(image.imageId)
          ? emit('unmarkDelete', image.imageId)
          : emit('markDelete', image.imageId)"
      />

      <EmoticonImageTile
        v-for="(item, index) in newImages"
        :key="item.clientId"
        :src="item.preview"
        :alt="existingImages.length > 0
          ? t('emoticon.form.newImageAlt', { index: index + 1 })
          : t('emoticon.form.imageAlt', { index: index + 1 })"
        :action-label="$t('common.delete')"
        :action-title="$t('common.delete')"
        action="delete"
        :variant="existingImages.length > 0 ? 'new' : 'default'"
        @action="emit('removeNew', item.clientId)"
      />

      <input
        :id="inputId"
        ref="fileInput"
        type="file"
        :name="inputName"
        :accept="accept"
        multiple
        :disabled="!allowAdd || currentCount >= maxCount"
        class="hidden"
        @change="emit('select', $event)"
      />
      <div v-if="allowAdd && currentCount < maxCount" class="min-w-0">
        <button
          type="button"
          :aria-label="$t('common.add')"
          :title="$t('common.add')"
          class="aspect-square w-full border-2 border-dashed nv-border rounded flex flex-col items-center justify-center nv-text-subtle hover:border-[var(--nv-focus)] hover:text-[var(--nv-accent)] transition-colors"
          @click="openFileInput"
        >
          <Plus class="w-6 h-6" />
        </button>
      </div>
    </div>

    <slot name="meta" />
  </div>
</template>

<style scoped>
.emoticon-image-grid {
  grid-template-columns: repeat(auto-fill, minmax(min(4.5rem, 100%), 1fr));
}
</style>
