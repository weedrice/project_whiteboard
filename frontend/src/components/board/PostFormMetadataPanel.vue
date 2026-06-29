<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import PostTags from '@/components/tag/PostTags.vue'

export type PostFormCategoryOption = {
  categoryId: number
  name: string
  minWriteRole?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  layout: 'mobile' | 'desktop'
  categories: PostFormCategoryOption[]
  categoryId: string | number
  tags: string[]
  isNotice: boolean
  isNsfw: boolean
  isSpoiler: boolean
  isSecret: boolean
  hideCategory?: boolean
  hideTags?: boolean
  showNotice?: boolean
  canShowNsfw?: boolean
  hideSpoiler?: boolean
  hideSecret?: boolean
}>(), {
  hideCategory: false,
  hideTags: false,
  showNotice: false,
  canShowNsfw: false,
  hideSpoiler: false,
  hideSecret: false,
})

const emit = defineEmits<{
  (event: 'update:categoryId', value: string | number): void
  (event: 'update:tags', value: string[]): void
  (event: 'update:isNotice', value: boolean): void
  (event: 'update:isNsfw', value: boolean): void
  (event: 'update:isSpoiler', value: boolean): void
  (event: 'update:isSecret', value: boolean): void
}>()

const { t } = useI18n()
const isMobile = computed(() => props.layout === 'mobile')
const categoryInputId = computed(() => isMobile.value ? 'category-mobile' : 'category')
const tagsInputId = computed(() => isMobile.value ? 'post-tags-input-mobile' : 'post-tags-input-desktop')
const checkboxSuffix = computed(() => isMobile.value ? '-m' : '')
</script>

<template>
  <div
    v-if="layout === 'mobile'"
    class="mb-4 flex flex-wrap items-center gap-2 lg:hidden"
  >
    <div v-if="!hideCategory && categories.length > 0" class="min-w-[10rem] flex-1">
      <BaseSelect
        :id="categoryInputId"
        :model-value="categoryId"
        :label="t('common.category')"
        @update:model-value="emit('update:categoryId', $event)"
      >
        <option value="" disabled>{{ t('board.writePost.selectCategory') }}</option>
        <option
          v-for="cat in categories"
          :key="cat.categoryId"
          :value="cat.categoryId"
          :disabled="cat.disabled"
        >
          {{ cat.name }}
        </option>
      </BaseSelect>
    </div>
    <div class="flex flex-wrap gap-2">
      <BaseCheckbox
        v-if="showNotice"
        :id="`isNotice${checkboxSuffix}`"
        :model-value="isNotice"
        :label="t('common.notice')"
        @update:model-value="emit('update:isNotice', $event)"
      />
      <BaseCheckbox
        v-if="canShowNsfw"
        :id="`nsfw${checkboxSuffix}`"
        :model-value="isNsfw"
        :label="t('board.writePost.nsfw')"
        @update:model-value="emit('update:isNsfw', $event)"
      />
      <BaseCheckbox
        v-if="!hideSpoiler"
        :id="`spoiler${checkboxSuffix}`"
        :model-value="isSpoiler"
        :label="t('board.writePost.spoiler')"
        @update:model-value="emit('update:isSpoiler', $event)"
      />
      <BaseCheckbox
        v-if="!hideSecret"
        :id="`secret${checkboxSuffix}`"
        :model-value="isSecret"
        :label="t('board.writePost.secret')"
        @update:model-value="emit('update:isSecret', $event)"
      />
    </div>
  </div>

  <template v-else>
    <div v-if="!hideCategory && categories.length > 0" class="nv-compose-side-section mb-4 hidden lg:block">
      <BaseSelect
        :id="categoryInputId"
        :model-value="categoryId"
        :label="t('common.category')"
        @update:model-value="emit('update:categoryId', $event)"
      >
        <option value="" disabled>{{ t('board.writePost.selectCategory') }}</option>
        <option
          v-for="cat in categories"
          :key="cat.categoryId"
          :value="cat.categoryId"
          :disabled="cat.disabled"
        >
          {{ cat.name }}
        </option>
      </BaseSelect>
    </div>

    <div v-if="!hideTags" class="nv-compose-side-section mb-4 hidden lg:block">
      <label :for="tagsInputId" class="mb-2 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
        {{ t('common.tags') }}
      </label>
      <PostTags
        :model-value="tags"
        :input-id="tagsInputId"
        @update:model-value="emit('update:tags', $event)"
      />
    </div>

    <div class="nv-compose-side-section space-y-3">
      <BaseCheckbox
        v-if="showNotice"
        :id="`isNotice${checkboxSuffix}`"
        :model-value="isNotice"
        :label="t('common.notice')"
        :description="t('board.writePost.noticeDesc')"
        @update:model-value="emit('update:isNotice', $event)"
      />
      <BaseCheckbox
        v-if="canShowNsfw"
        :id="`nsfw${checkboxSuffix}`"
        :model-value="isNsfw"
        :label="t('board.writePost.nsfw')"
        :description="t('board.writePost.nsfwDesc')"
        @update:model-value="emit('update:isNsfw', $event)"
      />
      <BaseCheckbox
        v-if="!hideSpoiler"
        :id="`spoiler${checkboxSuffix}`"
        :model-value="isSpoiler"
        :label="t('board.writePost.spoiler')"
        :description="t('board.writePost.spoilerDesc')"
        @update:model-value="emit('update:isSpoiler', $event)"
      />
      <BaseCheckbox
        v-if="!hideSecret"
        :id="`secret${checkboxSuffix}`"
        :model-value="isSecret"
        :label="t('board.writePost.secret')"
        :description="t('board.writePost.secretDesc')"
        @update:model-value="emit('update:isSecret', $event)"
      />
    </div>
  </template>
</template>
