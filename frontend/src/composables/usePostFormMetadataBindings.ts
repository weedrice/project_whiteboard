import { computed, type Ref } from 'vue'
import type { PostFormCategoryOption } from '@/composables/usePostFormCategoryOptions'

type PostComposerForm = {
  categoryId: string | number
  tags: string[]
  isNotice: boolean
  isNsfw: boolean
  isSpoiler: boolean
  isSecret: boolean
}

type UsePostFormMetadataBindingsParams = {
  form: Ref<PostComposerForm>
  categories: Ref<PostFormCategoryOption[]>
  showNotice: Ref<boolean>
  canShowNsfw: Ref<boolean>
  hideCategory: () => boolean | undefined
  hideTags: () => boolean | undefined
  hideSpoiler: () => boolean | undefined
  hideSecret: () => boolean | undefined
}

export function usePostFormMetadataBindings({
  form,
  categories,
  showNotice,
  canShowNsfw,
  hideCategory,
  hideTags,
  hideSpoiler,
  hideSecret,
}: UsePostFormMetadataBindingsParams) {
  const metadataPanelProps = computed(() => ({
    categories: categories.value,
    categoryId: form.value.categoryId,
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

  const metadataPanelHandlers = {
    'update:categoryId': (value: string | number) => {
      form.value.categoryId = value
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
