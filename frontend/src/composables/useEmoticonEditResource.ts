import { onMounted, watch, type ComputedRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { useEmoticonPermissions } from '@/composables/useEmoticonPermissions'
import type { EmoticonMaster } from '@/types/emoticon'

interface EmoticonEditUser {
  userId?: number
}

interface UseEmoticonEditResourceOptions {
  emoticonId: ComputedRef<number>
  isAuthenticated: () => boolean
  getUser: () => EmoticonEditUser | null | undefined
  onUnauthenticated: () => void
  onForbidden: () => void
}

export function useEmoticonEditResource({
  emoticonId,
  isAuthenticated,
  getUser,
  onUnauthenticated,
  onForbidden,
}: UseEmoticonEditResourceOptions) {
  const { data: emoticon, isLoading } = useQuery({
    queryKey: ['emoticon', emoticonId],
    queryFn: async () => {
      return emoticonApi.getEmoticonData(emoticonId.value)
    },
    enabled: () => !!emoticonId.value
  })

  const { isOwner } = useEmoticonPermissions({
    isAuthenticated,
    getCreatorId: () => (emoticon.value as EmoticonMaster | undefined)?.creatorId,
    getUserId: () => getUser()?.userId,
  })

  onMounted(() => {
    if (!isAuthenticated()) {
      onUnauthenticated()
    }
  })

  watch([emoticon, getUser], ([emoticonData, user]) => {
    if (emoticonData && user && emoticonData.creatorId !== user.userId) {
      onForbidden()
    }
  }, { immediate: true })

  return {
    emoticon,
    isLoading,
    isOwner,
  }
}
