import { computed, type Ref } from 'vue'
import type { PostFormCategoryOption } from '@/features/board/posts/form/usePostFormCategoryOptions'

type PostComposerForm = {
  categoryId: string | number
  seriesId: string | number
  tags: string[]
  isNotice: boolean
  isNsfw: boolean
  isSpoiler: boolean
  isSecret: boolean
}

type UsePostFormMetadataBindingsParams = {
  form: Ref<PostComposerForm>
  categories: Ref<PostFormCategoryOption[]>
  seriesOptions: Ref<Array<{ seriesId: number, title: string }>>
  newSeriesTitle: Ref<string>
  isCreatingSeries: Ref<boolean>
  showNotice: Ref<boolean>
  canShowNsfw: Ref<boolean>
  hideCategory: () => boolean | undefined
  hideTags: () => boolean | undefined
  hideSpoiler: () => boolean | undefined
  hideSecret: () => boolean | undefined
  createSeries: () => void | Promise<void>
}

export type PostFormMetadataPanelProps = {
  categories: PostFormCategoryOption[]
  categoryId: string | number
  seriesId: string | number
  seriesOptions: Array<{ seriesId: number, title: string }>
  newSeriesTitle: string
  isCreatingSeries: boolean
  tags: string[]
  isNotice: boolean
  isNsfw: boolean
  isSpoiler: boolean
  isSecret: boolean
  hideCategory?: boolean
  hideTags?: boolean
  showNotice: boolean
  canShowNsfw: boolean
  hideSpoiler?: boolean
  hideSecret?: boolean
}

export type PostFormMetadataPanelHandlers = {
  'update:categoryId': (value: string | number) => void
  'update:seriesId': (value: string | number) => void
  'update:newSeriesTitle': (value: string | number) => void
  'create-series': () => void | Promise<void>
  'update:tags': (value: string[]) => void
  'update:isNotice': (value: boolean) => void
  'update:isNsfw': (value: boolean) => void
  'update:isSpoiler': (value: boolean) => void
  'update:isSecret': (value: boolean) => void
}

export function usePostFormMetadataBindings({
  form,
  categories,
  seriesOptions,
  newSeriesTitle,
  isCreatingSeries,
  showNotice,
  canShowNsfw,
  hideCategory,
  hideTags,
  hideSpoiler,
  hideSecret,
  createSeries,
}: UsePostFormMetadataBindingsParams) {
  const metadataPanelProps = computed<PostFormMetadataPanelProps>(() => ({
    categories: categories.value,
    categoryId: form.value.categoryId,
    seriesId: form.value.seriesId,
    seriesOptions: seriesOptions.value,
    newSeriesTitle: newSeriesTitle.value,
    isCreatingSeries: isCreatingSeries.value,
    tags: form.value.tags,
    isNotice: form.value.isNotice,
    isNsfw: form.value.isNsfw,
    isSpoiler: form.value.isSpoiler,
    isSecret: form.value.isSecret,
    hideCategory: hideCategory(),
    hideTags: hideTags(),
    showNotice: showNotice.value,
    canShowNsfw: canShowNsfw.value,
    hideSpoiler: hideSpoiler(),
    hideSecret: hideSecret(),
  }))

  const metadataPanelHandlers: PostFormMetadataPanelHandlers = {
    'update:categoryId': (value: string | number) => {
      form.value.categoryId = value
    },
    'update:seriesId': (value: string | number) => {
      form.value.seriesId = value
    },
    'update:newSeriesTitle': (value: string | number) => {
      newSeriesTitle.value = String(value)
    },
    'create-series': createSeries,
    'update:tags': (value: string[]) => {
      form.value.tags = value
    },
    'update:isNotice': (value: boolean) => {
      form.value.isNotice = value
    },
    'update:isNsfw': (value: boolean) => {
      form.value.isNsfw = value
    },
    'update:isSpoiler': (value: boolean) => {
      form.value.isSpoiler = value
    },
    'update:isSecret': (value: boolean) => {
      form.value.isSecret = value
    },
  }

  return {
    metadataPanelProps,
    metadataPanelHandlers,
  }
}
