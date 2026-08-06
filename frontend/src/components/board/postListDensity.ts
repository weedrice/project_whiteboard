import { ref, watch } from 'vue'
import { Storage } from '@/utils/storage'

export type PostListDensity = 'default' | 'compact'

const POST_LIST_DENSITY_KEY = 'noviis:post-list-density'

export function usePostListDensity() {
  const storedDensity = Storage.getString(POST_LIST_DENSITY_KEY, 'default')
  const listDensity = ref<PostListDensity>(storedDensity === 'compact' ? 'compact' : 'default')

  watch(listDensity, (density) => {
    Storage.setString(POST_LIST_DENSITY_KEY, density)
  })

  return listDensity
}
