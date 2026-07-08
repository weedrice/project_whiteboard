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
  showNotice: Ref<boolean>
  canShowNsfw: Ref<boolean>
  hideCategory: () => boolean | undefined
  hideTags: () => boolean | undefined
  hideSpoiler: () => boolean | undefined
  hideSecret: () => boolean | undefined
}

export type PostFormMetadataPanelProps = {
  categories: PostFormCategoryOption[]
  categoryId: string | number
  seriesId: string | number
  seriesOptions: Array<{ seriesId: number, title: string }>
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
  showNotice,
  canShowNsfw,
  hideCategory,
  hideTags,
  hideSpoiler,
  hideSecret,
}: UsePostFormMetadataBindingsParams) {
  const metadataPanelProps = computed<PostFormMetadataPanelProps>(() => ({
    categories: categories.value,
    categoryId: form.value.categoryId,
    seriesId: form.value.seriesId,
    seriesOptions: seriesOptions.value,
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
