import { computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/composables/useAdmin'
import { useBoard } from '@/composables/useBoard'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'

export type SelectionMode = 'single' | 'multiple'
export type UserSelectSource = 'admin' | 'board-manager-candidates'

export interface SelectableUser {
  userId: number
  loginId: string
  displayName: string
  profileImageUrl?: string | null
  email?: string
  currentManager?: boolean
}

interface UseUserSelectSourceOptions {
  isOpen: Ref<boolean>
  selectionMode: Ref<SelectionMode>
  source: Ref<UserSelectSource>
  boardUrl: Ref<string>
  excludeUserIds: Ref<number[]>
  searchQuery: Ref<string>
}

export function useUserSelectSource({
  isOpen,
  selectionMode,
  source,
  boardUrl,
  excludeUserIds,
  searchQuery,
}: UseUserSelectSourceOptions) {
  const { t } = useI18n()
  const { useUsers } = useAdmin()
  const { useBoardManagerCandidates } = useBoard()

  const params = computed(() => ({
    page: 0,
    size: 10,
    q: searchQuery.value
  }))
  const isBoardCandidateSource = computed(() => source.value === 'board-manager-candidates')
  const isUsersQueryEnabled = computed(() => isOpen.value && source.value === 'admin')
  const isBoardCandidatesQueryEnabled = computed(() => (
    isOpen.value && isBoardCandidateSource.value && !!boardUrl.value
  ))

  const { data: adminUsersData, isLoading: isAdminUsersLoading } = useUsers(params, isUsersQueryEnabled)
  const { data: boardCandidatesData, isLoading: isBoardCandidatesLoading } = useBoardManagerCandidates(
    boardUrl,
    params,
    isBoardCandidatesQueryEnabled
  )

  const isMultiMode = computed(() => selectionMode.value === 'multiple')
  const showEmailColumn = computed(() => !isBoardCandidateSource.value)
  const usersData = computed(() => (isBoardCandidateSource.value ? boardCandidatesData.value : adminUsersData.value))
  const isLoading = computed(() => (isBoardCandidateSource.value ? isBoardCandidatesLoading.value : isAdminUsersLoading.value))
  const userColumns = computed<TableColumn[]>(() => {
    const columns: TableColumn[] = []

    if (isMultiMode.value) {
      columns.push({ key: 'selection', label: '', width: '2.5rem', align: 'center' })
    }

    columns.push({ key: 'loginId', label: t('common.loginId') })
    columns.push({ key: 'displayName', label: t('common.displayName') })

    if (showEmailColumn.value) {
      columns.push({ key: 'email', label: t('common.email') })
    }

    return columns
  })

  const filteredUsers = computed<SelectableUser[]>(() => {
    const list = usersData.value?.content || []
    if (!excludeUserIds.value.length) return list
    const excluded = new Set(excludeUserIds.value)
    return list.filter((user) => !excluded.has(user.userId))
  })

  return {
    filteredUsers,
    isBoardCandidateSource,
    isLoading,
    isMultiMode,
    userColumns,
  }
}
