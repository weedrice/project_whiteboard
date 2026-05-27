import { type ComputedRef } from 'vue'
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
}

export function useEmoticonEditResource({
  emoticonId,
  isAuthenticated,
  getUser,
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

  return {
    emoticon,
    isLoading,
    isOwner,
  }
}
