<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { tagApi } from '@/api/tag'
import { unwrapAxiosApiData } from '@/api/response'
import { POST_SERIES_TITLE_MAX_LENGTH } from '@/utils/postForm'

export type PostFormCategoryOption = {
  categoryId: number
  name: string
  minWriteRole?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  layout: 'mobile' | 'desktop'
  categories: PostFormCategoryOption[]
  title: string
  content: string
  boardUrl?: string
  seriesOptions: Array<{ seriesId: number, title: string }>
  categoryId: string | number
  seriesId: string | number
  newSeriesTitle: string
  isCreatingSeries: boolean
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
  (event: 'update:seriesId', value: string | number): void
  (event: 'update:newSeriesTitle', value: string | number): void
  (event: 'create-series'): void
  (event: 'update:tags', value: string[]): void
  (event: 'update:isNotice', value: boolean): void
  (event: 'update:isNsfw', value: boolean): void
  (event: 'update:isSpoiler', value: boolean): void
  (event: 'update:isSecret', value: boolean): void
}>()

const { t } = useI18n()
const isMobile = computed(() => props.layout === 'mobile')
const categoryInputId = computed(() => isMobile.value ? 'category-mobile' : 'category')
const seriesInputId = computed(() => isMobile.value ? 'series-mobile' : 'series')
const newSeriesInputId = computed(() => isMobile.value ? 'new-series-mobile' : 'new-series')
const tagsInputId = computed(() => isMobile.value ? 'post-tags-input-mobile' : 'post-tags-input-desktop')
const checkboxSuffix = computed(() => isMobile.value ? '-m' : '')
const isSuggestingTags = ref(false)
const suggestedTags = ref<string[]>([])
const tagSuggestionFailed = ref(false)
const visibleSuggestedTags = computed(() => suggestedTags.value.filter((tag) => !hasTag(tag)))
let inputRevision = 0
let suggestionSequence = 0
let suggestionController: AbortController | null = null

type BooleanUpdateEvent = 'update:isNotice' | 'update:isNsfw' | 'update:isSpoiler' | 'update:isSecret'

const emitBooleanUpdate = (event: BooleanUpdateEvent, value: boolean | unknown[]) => {
  const checked = Array.isArray(value) ? value.length > 0 : value

  switch (event) {
    case 'update:isNotice':
      emit('update:isNotice', checked)
      break
    case 'update:isNsfw':
      emit('update:isNsfw', checked)
      break
    case 'update:isSpoiler':
      emit('update:isSpoiler', checked)
      break
    case 'update:isSecret':
      emit('update:isSecret', checked)
      break
  }
}

const hasTag = (tag: string) => props.tags.some((existing) => existing.trim().toLowerCase() === tag.trim().toLowerCase())

async function suggestTags() {
  if (isSuggestingTags.value) return
  const revision = inputRevision
  const sequence = ++suggestionSequence
  const controller = new AbortController()
  const request = {
    title: props.title,
    contents: props.content,
    boardUrl: props.boardUrl,
    existingTags: [...props.tags],
  }
  suggestionController?.abort()
  suggestionController = controller
  isSuggestingTags.value = true
  tagSuggestionFailed.value = false
  try {
    const response = unwrapAxiosApiData(await tagApi.suggestTags(request, { signal: controller.signal }))
    if (
      controller.signal.aborted
      || sequence !== suggestionSequence
      || revision !== inputRevision
    ) return
    suggestedTags.value = response.suggestions ?? []
  } catch {
    if (
      controller.signal.aborted
      || sequence !== suggestionSequence
      || revision !== inputRevision
    ) return
    tagSuggestionFailed.value = true
  } finally {
    if (sequence === suggestionSequence) {
      isSuggestingTags.value = false
      if (suggestionController === controller) suggestionController = null
    }
  }
}

watch([
  () => props.title,
  () => props.content,
  () => props.boardUrl,
  () => props.tags,
], () => {
  inputRevision += 1
  suggestionSequence += 1
  suggestionController?.abort()
  suggestionController = null
  isSuggestingTags.value = false
  tagSuggestionFailed.value = false
  suggestedTags.value = []
}, { deep: true, flush: 'sync' })

onUnmounted(() => {
  suggestionSequence += 1
  suggestionController?.abort()
  suggestionController = null
})

function addSuggestedTag(tag: string) {
  if (hasTag(tag)) return
  emit('update:tags', [...props.tags, tag])
}
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
      <BaseSelect
        :id="seriesInputId"
        :model-value="seriesId"
        :label="t('board.writePost.series')"
        class="min-w-[10rem]"
        @update:model-value="emit('update:seriesId', $event)"
      >
        <option value="">{{ t('board.writePost.noSeries') }}</option>
        <option v-for="series in seriesOptions" :key="series.seriesId" :value="series.seriesId">
          {{ series.title }}
        </option>
      </BaseSelect>
      <div class="flex min-w-[14rem] flex-1 items-end gap-2">
        <BaseInput
          :id="newSeriesInputId"
          :model-value="newSeriesTitle"
          :maxlength="POST_SERIES_TITLE_MAX_LENGTH"
          :label="t('board.writePost.newSeries')"
          :placeholder="t('board.writePost.newSeriesPlaceholder')"
          hide-label
          input-class="h-9"
          :disabled="isCreatingSeries"
          @update:model-value="emit('update:newSeriesTitle', $event)"
          @keydown.enter.prevent="emit('create-series')"
        />
        <BaseButton
          type="button"
          size="sm"
          variant="secondary"
          class="h-9 shrink-0"
          :loading="isCreatingSeries"
          :disabled="!String(newSeriesTitle).trim()"
          @click="emit('create-series')"
        >
          {{ t('board.writePost.createSeries') }}
        </BaseButton>
      </div>
      <BaseCheckbox
        v-if="showNotice"
        :id="`isNotice${checkboxSuffix}`"
        :model-value="isNotice"
        :label="t('common.notice')"
        @update:model-value="emitBooleanUpdate('update:isNotice', $event)"
      />
      <BaseCheckbox
        v-if="canShowNsfw"
        :id="`nsfw${checkboxSuffix}`"
        :model-value="isNsfw"
        :label="t('board.writePost.nsfw')"
        @update:model-value="emitBooleanUpdate('update:isNsfw', $event)"
      />
      <BaseCheckbox
        v-if="!hideSpoiler"
        :id="`spoiler${checkboxSuffix}`"
        :model-value="isSpoiler"
        :label="t('board.writePost.spoiler')"
        @update:model-value="emitBooleanUpdate('update:isSpoiler', $event)"
      />
      <BaseCheckbox
        v-if="!hideSecret"
        :id="`secret${checkboxSuffix}`"
        :model-value="isSecret"
        :label="t('board.writePost.secret')"
        @update:model-value="emitBooleanUpdate('update:isSecret', $event)"
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
      <div class="mb-2 flex items-center justify-between gap-2">
        <label :for="tagsInputId" class="block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
          {{ t('common.tags') }}
        </label>
        <BaseButton
          type="button"
          size="sm"
          variant="ghost"
          :loading="isSuggestingTags"
          @click="suggestTags"
        >
          {{ isSuggestingTags ? t('board.writePost.suggestingTags') : t('board.writePost.suggestTags') }}
        </BaseButton>
      </div>
      <PostTags
        :model-value="tags"
        :input-id="tagsInputId"
        @update:model-value="emit('update:tags', $event)"
      />
      <div v-if="visibleSuggestedTags.length > 0" class="mt-2 flex flex-wrap gap-1.5">
        <button
          v-for="tag in visibleSuggestedTags"
          :key="tag"
          type="button"
          class="rounded-full border border-[var(--nv-line)] px-2 py-1 text-xs font-medium nv-hover-surface"
          @click="addSuggestedTag(tag)"
        >
          #{{ tag }}
        </button>
      </div>
      <div v-else-if="tagSuggestionFailed" class="mt-2 text-sm nv-form-error" role="alert">
        <p>{{ t('board.writePost.tagSuggestionFailed') }}</p>
        <BaseButton type="button" size="sm" variant="secondary" class="mt-2" @click="suggestTags">
          {{ t('common.error.retry') }}
        </BaseButton>
      </div>
    </div>

    <div class="nv-compose-side-section mb-4 hidden lg:block">
      <BaseSelect
        :id="seriesInputId"
        :model-value="seriesId"
        :label="t('board.writePost.series')"
        @update:model-value="emit('update:seriesId', $event)"
      >
        <option value="">{{ t('board.writePost.noSeries') }}</option>
        <option v-for="series in seriesOptions" :key="series.seriesId" :value="series.seriesId">
          {{ series.title }}
        </option>
      </BaseSelect>
      <div class="mt-3 flex gap-2">
        <BaseInput
          :id="newSeriesInputId"
          :model-value="newSeriesTitle"
          :maxlength="POST_SERIES_TITLE_MAX_LENGTH"
          :label="t('board.writePost.newSeries')"
          :placeholder="t('board.writePost.newSeriesPlaceholder')"
          hide-label
          input-class="h-9"
          :disabled="isCreatingSeries"
          @update:model-value="emit('update:newSeriesTitle', $event)"
          @keydown.enter.prevent="emit('create-series')"
        />
        <BaseButton
          type="button"
          size="sm"
          variant="secondary"
          class="h-9 shrink-0"
          :loading="isCreatingSeries"
          :disabled="!String(newSeriesTitle).trim()"
          @click="emit('create-series')"
        >
          {{ t('board.writePost.createSeries') }}
        </BaseButton>
      </div>
    </div>

    <div class="nv-compose-side-section space-y-3">
      <BaseCheckbox
        v-if="showNotice"
        :id="`isNotice${checkboxSuffix}`"
        :model-value="isNotice"
        :label="t('common.notice')"
        :description="t('board.writePost.noticeDesc')"
        @update:model-value="emitBooleanUpdate('update:isNotice', $event)"
      />
      <BaseCheckbox
        v-if="canShowNsfw"
        :id="`nsfw${checkboxSuffix}`"
        :model-value="isNsfw"
        :label="t('board.writePost.nsfw')"
        :description="t('board.writePost.nsfwDesc')"
        @update:model-value="emitBooleanUpdate('update:isNsfw', $event)"
      />
      <BaseCheckbox
        v-if="!hideSpoiler"
        :id="`spoiler${checkboxSuffix}`"
        :model-value="isSpoiler"
        :label="t('board.writePost.spoiler')"
        :description="t('board.writePost.spoilerDesc')"
        @update:model-value="emitBooleanUpdate('update:isSpoiler', $event)"
      />
      <BaseCheckbox
        v-if="!hideSecret"
        :id="`secret${checkboxSuffix}`"
        :model-value="isSecret"
        :label="t('board.writePost.secret')"
        :description="t('board.writePost.secretDesc')"
        @update:model-value="emitBooleanUpdate('update:isSecret', $event)"
      />
    </div>
  </template>
</template>
