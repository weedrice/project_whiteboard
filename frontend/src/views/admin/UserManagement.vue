<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Search, Ban, VolumeX, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import UserDetailModal from '@/components/admin/UserDetailModal.vue'
import { formatDate } from '@/utils/date'
import { useConfirm } from '@/composables/useConfirm'
import { usePrompt } from '@/composables/usePrompt'
import type { User } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { prompt } = usePrompt()
const { useUsers, useUpdateUserStatus, useSanctionUser } = useAdmin()

const page = ref(0)
const size = ref(20)
const searchQuery = ref('')
const statusFilter = ref('')
const roleFilter = ref('')
const emailVerifiedFilter = ref('')
const superAdminFilter = ref('')
const withdrawnFilter = ref('')
const createdFrom = ref('')
const createdTo = ref('')
const lastLoginFrom = ref('')
const lastLoginTo = ref('')
const minActivityCount = ref('')
const sort = ref('createdAt,desc')

const params = computed(() => ({
  page: page.value,
  size: size.value,
  q: searchQuery.value || undefined,
  status: statusFilter.value || undefined,
  role: roleFilter.value || undefined,
  isEmailVerified: emailVerifiedFilter.value === '' ? undefined : emailVerifiedFilter.value === 'true',
  isSuperAdmin: superAdminFilter.value === '' ? undefined : superAdminFilter.value === 'true',
  isWithdrawn: withdrawnFilter.value === '' ? undefined : withdrawnFilter.value === 'true',
  createdFrom: createdFrom.value || undefined,
  createdTo: createdTo.value || undefined,
  lastLoginFrom: lastLoginFrom.value || undefined,
  lastLoginTo: lastLoginTo.value || undefined,
  minActivityCount: minActivityCount.value ? Number(minActivityCount.value) : undefined,
  sort: sort.value || undefined
}))

const { data: usersData, isLoading } = useUsers(params)
const { mutateAsync: updateUserStatus } = useUpdateUserStatus()
const { mutateAsync: sanctionUser } = useSanctionUser()

const users = computed(() => usersData.value?.content || [])
const totalCount = computed(() => usersData.value?.totalElements || 0)
const totalPages = computed(() => usersData.value?.totalPages || 0)
const currentPage = computed(() => usersData.value?.number ?? page.value)

const isDetailModalOpen = ref(false)
const selectedUserId = ref<number | null>(null)

function openDetailModal(user: User) {
  selectedUserId.value = user.userId
  isDetailModalOpen.value = true
}

function getStatusVariant(status: string) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'SUSPENDED' || status === 'SANCTIONED') return 'danger'
  if (status === 'DELETED') return 'warning'
  return 'gray'
}

async function handleStatusChange(user: User, status: 'ACTIVE' | 'SUSPENDED') {
  const isConfirmed = await confirm(t('admin.users.messages.confirmStatusChange', { status }))
  if (!isConfirmed) return
  try {
    await updateUserStatus({ userId: user.userId, status })
    toastStore.addToast(t('admin.users.messages.statusChanged'), 'success')
  } catch {
    // Error handled globally
  }
}

async function handleSanction(user: User, type: 'BAN' | 'MUTE') {
  const typeLabel = type === 'BAN' ? t('admin.users.actions.ban') : t('admin.users.actions.mute')
  const reason = await prompt(
    t('admin.users.messages.enterReason', { type: typeLabel }),
    t('admin.users.messages.sanctionTitle', { type: typeLabel })
  )
  if (!reason) return

  try {
    await sanctionUser({ userId: user.userId, type, reason })
    toastStore.addToast(t('admin.users.messages.sanctionComplete', { type: typeLabel }), 'success')
  } catch {
    // Error handled globally
  }
}

function resetFilters() {
  statusFilter.value = ''
  roleFilter.value = ''
  emailVerifiedFilter.value = ''
  superAdminFilter.value = ''
  withdrawnFilter.value = ''
  createdFrom.value = ''
  createdTo.value = ''
  lastLoginFrom.value = ''
  lastLoginTo.value = ''
  minActivityCount.value = ''
  sort.value = 'createdAt,desc'
}

function prevPage() {
  if (page.value > 0) page.value -= 1
}

function nextPage() {
  if (page.value + 1 < totalPages.value) page.value += 1
}

watch([
  searchQuery,
  statusFilter,
  roleFilter,
  emailVerifiedFilter,
  superAdminFilter,
  withdrawnFilter,
  createdFrom,
  createdTo,
  lastLoginFrom,
  lastLoginTo,
  minActivityCount,
  size,
  sort
], () => {
  page.value = 0
})

const columns = computed(() => [
  { key: 'userId', label: t('common.id'), width: '6%' },
  { key: 'profile', label: '프로필', width: '7%' },
  { key: 'loginId', label: t('common.loginId'), width: '10%' },
  { key: 'displayName', label: t('common.displayName'), width: '11%' },
  { key: 'email', label: t('common.email'), width: '16%' },
  { key: 'isEmailVerified', label: '이메일 인증', width: '8%' },
  { key: 'isSuperAdmin', label: '슈퍼관리자', width: '8%' },
  { key: 'status', label: t('admin.users.table.status'), width: '8%' },
  { key: 'lastLoginAt', label: '최근 로그인', width: '10%' },
  { key: 'createdAt', label: t('admin.users.table.joinedAt'), width: '10%' },
  { key: 'actions', label: '', align: 'right' as const, width: '6%' }
])
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.users.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.users.description') }}</p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <div class="w-72">
          <BaseInput v-model="searchQuery" :placeholder="t('admin.users.searchPlaceholder')" hideLabel>
            <template #prefix>
              <Search class="h-5 w-5 text-gray-400 dark:text-gray-500" aria-hidden="true" />
            </template>
          </BaseInput>
        </div>
      </div>
    </div>

    <div class="mt-6 rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-4">
        <select v-model="statusFilter" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
          <option value="">상태 전체</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="SUSPENDED">SUSPENDED</option>
          <option value="DELETED">DELETED</option>
        </select>
        <select v-model="roleFilter" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
          <option value="">권한 전체</option>
          <option value="USER">USER</option>
          <option value="SUPER_ADMIN">SUPER_ADMIN</option>
          <option value="BOARD_ADMIN">BOARD_ADMIN</option>
          <option value="MODERATOR">MODERATOR</option>
        </select>
        <select v-model="emailVerifiedFilter" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
          <option value="">이메일 인증 전체</option>
          <option value="true">인증</option>
          <option value="false">미인증</option>
        </select>
        <select v-model="superAdminFilter" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
          <option value="">슈퍼관리자 전체</option>
          <option value="true">Y</option>
          <option value="false">N</option>
        </select>
        <select v-model="withdrawnFilter" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
          <option value="">탈퇴 여부 전체</option>
          <option value="true">탈퇴</option>
          <option value="false">활성/정지</option>
        </select>
        <input v-model="createdFrom" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
        <input v-model="createdTo" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
        <input v-model="lastLoginFrom" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
        <input v-model="lastLoginTo" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
        <input v-model="minActivityCount" type="number" min="0" placeholder="최소 활동량" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
        <select v-model="sort" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
          <option value="createdAt,desc">가입일 최신순</option>
          <option value="createdAt,asc">가입일 오래된순</option>
          <option value="lastLoginAt,desc">최근 로그인순</option>
          <option value="loginId,asc">아이디 오름차순</option>
        </select>
        <div class="flex items-center gap-2">
          <select v-model.number="size" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
            <option :value="10">10개</option>
            <option :value="20">20개</option>
            <option :value="50">50개</option>
          </select>
          <BaseButton variant="secondary" size="sm" @click="resetFilters">초기화</BaseButton>
        </div>
      </div>
    </div>

    <div class="mt-6">
      <BaseTable :columns="columns" :items="users" :loading="isLoading" :emptyText="t('common.noData')">
        <template #cell-profile="{ item }">
          <div class="flex justify-center">
            <img v-if="item.profileImageUrl" :src="item.profileImageUrl" alt="profile" class="h-8 w-8 rounded-full object-cover" />
            <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-gray-200 text-xs font-semibold text-gray-600 dark:bg-gray-700 dark:text-gray-300">
              {{ (item.displayName || item.loginId || '?').slice(0, 1).toUpperCase() }}
            </div>
          </div>
        </template>

        <template #cell-isEmailVerified="{ item }">
          <BaseBadge :variant="item.isEmailVerified ? 'success' : 'gray'" size="sm">
            {{ item.isEmailVerified ? 'Y' : 'N' }}
          </BaseBadge>
        </template>

        <template #cell-isSuperAdmin="{ item }">
          <BaseBadge :variant="item.isSuperAdmin ? 'danger' : 'gray'" size="sm">
            {{ item.isSuperAdmin ? 'Y' : 'N' }}
          </BaseBadge>
        </template>

        <template #cell-status="{ item }">
          <BaseBadge :variant="getStatusVariant(item.status)" size="sm">
            {{ item.status }}
          </BaseBadge>
        </template>

        <template #cell-lastLoginAt="{ item }">
          {{ item.lastLoginAt ? formatDate(item.lastLoginAt) : '-' }}
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #cell-actions="{ item }">
          <div class="flex justify-end space-x-1">
            <BaseButton
              @click="openDetailModal(item)"
              variant="ghost"
              size="sm"
              class="p-1 text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300"
              :title="t('common.viewDetail')"
            >
              <Eye class="h-4 w-4" />
            </BaseButton>
            <BaseButton
              v-if="item.status !== 'DELETED'"
              @click="handleStatusChange(item, item.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE')"
              variant="ghost"
              size="sm"
              class="p-1 text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white"
              :title="item.status === 'ACTIVE' ? '정지' : '활성화'"
            >
              {{ item.status === 'ACTIVE' ? '정지' : '복구' }}
            </BaseButton>
            <BaseButton
              @click="handleSanction(item, 'BAN')"
              variant="ghost"
              size="sm"
              class="p-1 text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300"
              :title="t('admin.users.actions.ban')"
            >
              <Ban class="h-4 w-4" />
            </BaseButton>
            <BaseButton
              @click="handleSanction(item, 'MUTE')"
              variant="ghost"
              size="sm"
              class="p-1 text-orange-600 hover:text-orange-900 dark:text-orange-400 dark:hover:text-orange-300"
              :title="t('admin.users.actions.mute')"
            >
              <VolumeX class="h-4 w-4" />
            </BaseButton>
          </div>
        </template>
      </BaseTable>
    </div>

    <div v-if="totalPages > 0" class="mt-4 flex items-center justify-between rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm dark:border-gray-700 dark:bg-gray-800">
      <div class="text-gray-600 dark:text-gray-300">
        총 {{ totalCount.toLocaleString() }}명 / {{ currentPage + 1 }} / {{ totalPages }} 페이지
      </div>
      <div class="flex items-center gap-2">
        <BaseButton variant="secondary" size="sm" :disabled="currentPage <= 0" @click="prevPage">이전</BaseButton>
        <BaseButton variant="secondary" size="sm" :disabled="currentPage + 1 >= totalPages" @click="nextPage">다음</BaseButton>
      </div>
    </div>

    <UserDetailModal
      :isOpen="isDetailModalOpen"
      :userId="selectedUserId"
      @close="isDetailModalOpen = false"
    />
  </div>
</template>

