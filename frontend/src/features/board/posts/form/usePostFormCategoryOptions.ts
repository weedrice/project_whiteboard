import { computed, type Ref } from 'vue'
import { canWriteCategory } from '@/utils/board'

type CategoryLike = {
  categoryId: number
  name: string
  minWriteRole?: string
}

type PostWithCategory = {
  category?: CategoryLike | null
}

export type PostFormCategoryOption = CategoryLike & {
  disabled?: boolean
}

type UsePostFormCategoryOptionsParams<TCategory extends CategoryLike> = {
  categories: Ref<TCategory[]>
  board: Ref<{ isAdmin?: boolean } | null | undefined>
  post: Ref<PostWithCategory | null | undefined>
  selectedCategoryId: Ref<string | number>
  userRole: Ref<string | undefined>
}

export function usePostFormCategoryOptions<TCategory extends CategoryLike>({
  categories,
  board,
  post,
  selectedCategoryId,
  userRole,
}: UsePostFormCategoryOptionsParams<TCategory>) {
  const filteredCategories = computed<PostFormCategoryOption[]>(() => {
    const selectableCategories = categories.value.filter((cat) => canWriteCategory(
      cat,
      userRole.value,
      board.value?.isAdmin ?? false,
    ))
    const selectedId = Number(selectedCategoryId.value)
    const selectedCategory = categories.value.find((category) => category.categoryId === selectedId)
      ?? (post.value?.category?.categoryId === selectedId
        ? {
            categoryId: post.value.category.categoryId,
            name: post.value.category.name,
            minWriteRole: post.value.category.minWriteRole,
          }
        : null)
    if (!selectedCategory) return selectableCategories
    if (selectableCategories.some((category) => category.categoryId === selectedCategory.categoryId)) {
      return selectableCategories
    }
    return [
      {
        ...selectedCategory,
        disabled: true,
      },
      ...selectableCategories,
    ]
  })

  const firstCategoryId = computed(() => filteredCategories.value[0]?.categoryId)

  return {
    filteredCategories,
    firstCategoryId,
  }
}
