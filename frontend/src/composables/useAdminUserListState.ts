import { computed, reactive, ref, watch } from 'vue'

const DEFAULT_SORT = 'createdAt,desc'

export type AdminUserFilterForm = {
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

export interface AdminUserSearchParams {
  page: number
  size: number
  q?: string
  status?: string
  role?: string
  isEmailVerified?: boolean
  isSuperAdmin?: boolean
  isWithdrawn?: boolean
  createdFrom?: string
  createdTo?: string
  lastLoginFrom?: string
  lastLoginTo?: string
  sort?: string
}

export function createInitialAdminUserFilters(): AdminUserFilterForm {
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
    lastLoginTo: '',
  }
}

function toOptionalBoolean(value: string) {
  return value === '' ? undefined : value === 'true'
}

function normalizeFilters(filters: AdminUserFilterForm): AdminUserFilterForm {
  return {
    ...filters,
    q: filters.q.trim(),
  }
}

export function useAdminUserListState() {
  const page = ref(0)
  const size = ref(20)
  const sort = ref(DEFAULT_SORT)
  const filterForm = reactive<AdminUserFilterForm>(createInitialAdminUserFilters())
  const appliedFilters = ref<AdminUserFilterForm>(createInitialAdminUserFilters())

  const sortField = computed(() => sort.value.split(',')[0] || 'createdAt')
  const sortDirection = computed(() => sort.value.split(',')[1] || 'desc')

  const params = computed<AdminUserSearchParams>(() => ({
    page: page.value,
    size: size.value,
    q: appliedFilters.value.q || undefined,
    status: appliedFilters.value.status || undefined,
    role: appliedFilters.value.role || undefined,
    isEmailVerified: toOptionalBoolean(appliedFilters.value.emailVerified),
    isSuperAdmin: toOptionalBoolean(appliedFilters.value.superAdmin),
    isWithdrawn: toOptionalBoolean(appliedFilters.value.withdrawn),
    createdFrom: appliedFilters.value.createdFrom || undefined,
    createdTo: appliedFilters.value.createdTo || undefined,
    lastLoginFrom: appliedFilters.value.lastLoginFrom || undefined,
    lastLoginTo: appliedFilters.value.lastLoginTo || undefined,
    sort: sort.value || undefined,
  }))

  function resetFilters() {
    Object.assign(filterForm, createInitialAdminUserFilters())
    appliedFilters.value = createInitialAdminUserFilters()
    sort.value = DEFAULT_SORT
    page.value = 0
  }

  function applyFilters() {
    appliedFilters.value = normalizeFilters(filterForm)
    page.value = 0
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

  return {
    applyFilters,
    filterForm,
    getSortLabel,
    handleSort,
    page,
    params,
    resetFilters,
    size,
    sort,
    sortDirection,
    sortField,
  }
}
