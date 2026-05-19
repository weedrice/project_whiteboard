<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { Search } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import UserDetailModal from '@/components/admin/UserDetailModal.vue'
import { formatDateOnly } from '@/utils/date'
import { useConfirm } from '@/composables/useConfirm'
import type { User } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { useUsers, useUpdateUserStatus } = useAdmin()

const page = ref(0)
const size = ref(20)
const sort = ref('createdAt,desc')

type UserFilterForm = {
  q: string
  status: string
  role: string
  emailVerified: string
  superAdmin: string
  withdrawn: string
  createdFrom: string
  createdTo: string
  lastLoginFrom: string
  lastLoginTo: string
}

function createInitialFilters(): UserFilterForm {
  return {
    q: '',
    status: '',
    role: '',
    emailVerified: '',
    superAdmin: '',
    withdrawn: '',
    createdFrom: '',
    createdTo: '',
    lastLoginFrom: '',
    lastLoginTo: ''
  }
}

const filterForm = reactive<UserFilterForm>(createInitialFilters())
const appliedFilters = ref<UserFilterForm>(createInitialFilters())

const sortField = computed(() => sort.value.split(',')[0] || 'createdAt')
const sortDirection = computed(() => sort.value.split(',')[1] || 'desc')

const params = computed(() => ({
  page: page.value,
  size: size.value,
  q: appliedFilters.value.q || undefined,
  status: appliedFilters.value.status || undefined,
  role: appliedFilters.value.role || undefined,
  isEmailVerified: appliedFilters.value.emailVerified === '' ? undefined : appliedFilters.value.emailVerified === 'true',
  isSuperAdmin: appliedFilters.value.superAdmin === '' ? undefined : appliedFilters.value.superAdmin === 'true',
  isWithdrawn: appliedFilters.value.withdrawn === '' ? undefined : appliedFilters.value.withdrawn === 'true',
  createdFrom: appliedFilters.value.createdFrom || undefined,
  createdTo: appliedFilters.value.createdTo || undefined,
  lastLoginFrom: appliedFilters.value.lastLoginFrom || undefined,
  lastLoginTo: appliedFilters.value.lastLoginTo || undefined,
  sort: sort.value || undefined
}))

const { data: usersData, isLoading } = useUsers(params)
const { mutateAsync: updateUserStatus } = useUpdateUserStatus()

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

function getStatusLabel(status: string) {
  return t(`admin.users.status.${status}`)
}

function canChangeStatus(status: string) {
  return status === 'ACTIVE' || status === 'SUSPENDED'
}

function getNextStatus(status: string): 'ACTIVE' | 'SUSPENDED' {
  return status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
}

function getStatusActionLabel(status: string) {
  return status === 'ACTIVE' ? '정지' : '계정 활성화'
}

function getRoleLabel(role: string) {
  return t(`admin.users.role.${role}`)
}

async function handleStatusChange(user: User, status: 'ACTIVE' | 'SUSPENDED') {
  const isConfirmed = await confirm(t('admin.users.messages.confirmStatusChange', { action: getStatusActionLabel(user.status) }))
  if (!isConfirmed) return
  try {
    await updateUserStatus({ userId: user.userId, status })
    toastStore.addToast(t('admin.users.messages.statusChanged'), 'success')
  } catch {
    // Error handled globally
  }
}

function resetFilters() {
  Object.assign(filterForm, createInitialFilters())
  appliedFilters.value = createInitialFilters()
  sort.value = 'createdAt,desc'
  page.value = 0
}

function applyFilters() {
  appliedFilters.value = { ...filterForm }
  page.value = 0
}

function prevPage() {
  if (page.value > 0) page.value -= 1
}

function nextPage() {
  if (page.value + 1 < totalPages.value) page.value += 1
}

function getSortLabel(baseLabel: string, key: string) {
  if (sortField.value !== key) return baseLabel
  return `${baseLabel} ${sortDirection.value === 'asc' ? '▲' : '▼'}`
}

function handleSort(key: string) {
  if (sortField.value === key) {
    sort.value = `${key},${sortDirection.value === 'asc' ? 'desc' : 'asc'}`
    return
  }
  sort.value = `${key},asc`
}

watch([size, sort], () => {
  page.value = 0
})

const columns = computed(() => [
  { key: 'userId', label: getSortLabel(t('common.id'), 'userId'), width: '5%', sortable: true },
  { key: 'profile', label: '프로필', width: '7%', align: 'center' as const },
  { key: 'loginId', label: getSortLabel(t('common.loginId'), 'loginId'), width: '10%', sortable: true },
  { key: 'displayName', label: getSortLabel(t('common.displayName'), 'displayName'), width: '11%', sortable: true },
  { key: 'email', label: t('common.email'), width: '16%' },
  { key: 'isEmailVerified', label: getSortLabel('이메일 인증', 'isEmailVerified'), width: '8%', sortable: true, align: 'center' as const },
  { key: 'isSuperAdmin', label: getSortLabel('슈퍼관리자', 'isSuperAdmin'), width: '8%', sortable: true, align: 'center' as const },
  { key: 'status', label: getSortLabel(t('admin.users.table.status'), 'status'), width: '8%', sortable: true, align: 'center' as const },
  { key: 'lastLoginAt', label: getSortLabel('최근 로그인', 'lastLoginAt'), width: '10%', sortable: true },
  { key: 'createdAt', label: getSortLabel(t('admin.users.table.joinedAt'), 'createdAt'), width: '10%', sortable: true },
  { key: 'actions', label: '', align: 'right' as const, width: '7%' }
])
</script>

<template>
  <div>
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.users.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.users.description') }}</p>
      </div>
    </div>

    <div class="mt-6 rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <div class="flex flex-col items-start gap-4">
        <div class="flex flex-wrap items-end gap-3">
          <div class="flex w-36 flex-col gap-1">
            <label class="w-full text-left text-xs text-gray-500 dark:text-gray-400">상태</label>
            <select v-model="filterForm.status" class="w-full rounded-md border border-gray-300 pl-3 pr-9 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
              <option value="">전체</option>
              <option value="ACTIVE">{{ getStatusLabel('ACTIVE') }}</option>
              <option value="SUSPENDED">{{ getStatusLabel('SUSPENDED') }}</option>
              <option value="DELETED">{{ getStatusLabel('DELETED') }}</option>
            </select>
          </div>
          <div class="flex w-36 flex-col gap-1">
            <label class="w-full text-left text-xs text-gray-500 dark:text-gray-400">권한</label>
            <select v-model="filterForm.role" class="w-full rounded-md border border-gray-300 pl-3 pr-9 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
              <option value="">전체</option>
              <option value="USER">{{ getRoleLabel('USER') }}</option>
              <option value="SUPER_ADMIN">{{ getRoleLabel('SUPER_ADMIN') }}</option>
              <option value="BOARD_ADMIN">{{ getRoleLabel('BOARD_ADMIN') }}</option>
              <option value="MODERATOR">{{ getRoleLabel('MODERATOR') }}</option>
            </select>
          </div>
          <div class="flex w-36 flex-col gap-1">
            <label class="w-full text-left text-xs text-gray-500 dark:text-gray-400">이메일 인증</label>
            <select v-model="filterForm.emailVerified" class="w-full rounded-md border border-gray-300 pl-3 pr-9 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
              <option value="">전체</option>
              <option value="true">인증</option>
              <option value="false">미인증</option>
            </select>
          </div>
          <div class="flex w-36 flex-col gap-1">
            <label class="w-full text-left text-xs text-gray-500 dark:text-gray-400">슈퍼관리자</label>
            <select v-model="filterForm.superAdmin" class="w-full rounded-md border border-gray-300 pl-3 pr-9 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
              <option value="">전체</option>
              <option value="true">Y</option>
              <option value="false">N</option>
            </select>
          </div>
          <div class="flex w-36 flex-col gap-1">
            <label class="w-full text-left text-xs text-gray-500 dark:text-gray-400">탈퇴 여부</label>
            <select v-model="filterForm.withdrawn" class="w-full rounded-md border border-gray-300 pl-3 pr-9 py-2 text-sm dark:border-gray-600 dark:bg-gray-900">
              <option value="">전체</option>
              <option value="true">탈퇴</option>
              <option value="false">활성/정지</option>
            </select>
          </div>
        </div>

        <div class="flex flex-wrap items-end gap-3">
          <div class="flex w-44 flex-col gap-1">
            <label class="text-xs text-gray-500 dark:text-gray-400">가입일 시작</label>
            <input v-model="filterForm.createdFrom" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
          </div>
          <div class="flex w-44 flex-col gap-1">
            <label class="text-xs text-gray-500 dark:text-gray-400">가입일 종료</label>
            <input v-model="filterForm.createdTo" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
          </div>
          <div class="flex w-44 flex-col gap-1">
            <label class="text-xs text-gray-500 dark:text-gray-400">최근 로그인 시작</label>
            <input v-model="filterForm.lastLoginFrom" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
          </div>
          <div class="flex w-44 flex-col gap-1">
            <label class="text-xs text-gray-500 dark:text-gray-400">최근 로그인 종료</label>
            <input v-model="filterForm.lastLoginTo" type="date" class="rounded-md border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-900" />
          </div>
        </div>

        <div class="flex flex-wrap items-end gap-3">
          <div class="w-80 max-w-full">
            <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">사용자 검색</label>
            <BaseInput v-model="filterForm.q" :placeholder="t('admin.users.searchPlaceholder')" hideLabel @keyup.enter="applyFilters">
              <template #prefix>
                <Search class="h-5 w-5 text-gray-400 dark:text-gray-500" aria-hidden="true" />
              </template>
            </BaseInput>
          </div>
          <div class="flex items-center gap-2">
            <BaseButton variant="primary" size="sm" @click="applyFilters">검색</BaseButton>
            <BaseButton variant="secondary" size="sm" @click="resetFilters">초기화</BaseButton>
          </div>
        </div>
      </div>
    </div>

    <div class="mt-6 flex items-center justify-between rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm dark:border-gray-700 dark:bg-gray-800">
      <div class="text-gray-600 dark:text-gray-300">
        <div v-if="totalPages > 0">총 {{ totalCount.toLocaleString() }}명 / {{ currentPage + 1 }} / {{ totalPages }} 페이지</div>
        <div v-else>총 {{ totalCount.toLocaleString() }}명</div>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">사용자 행을 더블클릭하면 상세 팝업이 열립니다.</p>
      </div>
      <div class="flex items-center gap-2">
        <label class="text-xs text-gray-500 dark:text-gray-400">페이지 당</label>
        <select v-model.number="size" class="rounded-md border border-gray-300 px-2 py-1 text-xs dark:border-gray-600 dark:bg-gray-900">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
        <BaseButton variant="secondary" size="sm" :disabled="currentPage <= 0" @click="prevPage">이전</BaseButton>
        <BaseButton variant="secondary" size="sm" :disabled="totalPages === 0 || currentPage + 1 >= totalPages" @click="nextPage">다음</BaseButton>
      </div>
    </div>

    <div class="mt-4">
      <BaseTable
        :columns="columns"
        :items="users"
        row-key="userId"
        :loading="isLoading"
        :emptyText="t('common.noData')"
        @sort="handleSort"
        @row-dblclick="openDetailModal"
      >
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
            {{ getStatusLabel(item.status) }}
          </BaseBadge>
        </template>

        <template #cell-lastLoginAt="{ item }">
          {{ item.lastLoginAt ? formatDateOnly(item.lastLoginAt) : '-' }}
        </template>

        <template #cell-createdAt="{ item }">
          {{ formatDateOnly(item.createdAt) }}
        </template>

        <template #cell-actions="{ item }">
          <div class="flex justify-end">
            <BaseButton
              v-if="canChangeStatus(item.status)"
              @click="handleStatusChange(item, getNextStatus(item.status))"
              variant="ghost"
              size="sm"
              class="p-1"
              :class="item.status === 'ACTIVE'
                ? 'text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300'
                : 'text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white'"
              :title="getStatusActionLabel(item.status)"
            >
              {{ getStatusActionLabel(item.status) }}
            </BaseButton>
          </div>
        </template>
      </BaseTable>
    </div>

    <UserDetailModal
      :isOpen="isDetailModalOpen"
      :userId="selectedUserId"
      @close="isDetailModalOpen = false"
    />
  </div>
</template>
