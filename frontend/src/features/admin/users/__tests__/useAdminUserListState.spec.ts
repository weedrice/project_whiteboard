import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import {
  createInitialAdminUserFilters,
  useAdminUserListState,
} from '../useAdminUserListState'

describe('useAdminUserListState', () => {
  it('builds default user query params', () => {
    const state = useAdminUserListState()

    expect(state.params.value).toEqual({
      page: 0,
      size: 20,
      q: undefined,
      status: undefined,
      role: undefined,
      isEmailVerified: undefined,
      isSuperAdmin: undefined,
      isWithdrawn: undefined,
      createdFrom: undefined,
      createdTo: undefined,
      lastLoginFrom: undefined,
      lastLoginTo: undefined,
      sort: 'createdAt,desc',
    })
    expect(state.getSortLabel('Joined', 'createdAt')).toBe('Joined ▼')
  })

  it('keeps draft filters out of params until they are applied', () => {
    const state = useAdminUserListState()

    state.filterForm.q = 'draft'

    expect(state.params.value.q).toBeUndefined()

    state.applyFilters()

    expect(state.params.value.q).toBe('draft')
  })

  it('applies filters with boolean and optional field conversion', () => {
    const state = useAdminUserListState()
    Object.assign(state.filterForm, {
      q: '  hong  ',
      status: 'ACTIVE',
      role: 'USER',
      emailVerified: 'true',
      superAdmin: 'false',
      withdrawn: 'false',
      createdFrom: '2026-01-01',
      createdTo: '2026-01-31',
      lastLoginFrom: '2026-02-01',
      lastLoginTo: '2026-02-28',
    })
    state.page.value = 4

    state.applyFilters()

    expect(state.page.value).toBe(0)
    expect(state.params.value).toMatchObject({
      q: 'hong',
      status: 'ACTIVE',
      role: 'USER',
      isEmailVerified: true,
      isSuperAdmin: false,
      isWithdrawn: false,
      createdFrom: '2026-01-01',
      createdTo: '2026-01-31',
      lastLoginFrom: '2026-02-01',
      lastLoginTo: '2026-02-28',
    })
  })

  it('omits blank search keywords after trimming filters', () => {
    const state = useAdminUserListState()
    state.filterForm.q = '   '

    state.applyFilters()

    expect(state.params.value.q).toBeUndefined()
  })

  it('keeps applied filters stable until apply is called again', () => {
    const state = useAdminUserListState()
    state.filterForm.q = 'applied'
    state.applyFilters()

    state.filterForm.q = 'draft'

    expect(state.params.value.q).toBe('applied')
  })

  it('resets filters, sort, and page to defaults', () => {
    const state = useAdminUserListState()
    Object.assign(state.filterForm, {
      q: 'hong',
      status: 'ACTIVE',
      emailVerified: 'true',
    })
    state.applyFilters()
    state.handleSort('loginId')
    state.page.value = 3

    state.resetFilters()

    expect(state.filterForm).toEqual(createInitialAdminUserFilters())
    expect(state.params.value).toEqual(expect.objectContaining({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      q: undefined,
      status: undefined,
      isEmailVerified: undefined,
    }))
    expect(state.getSortLabel('Joined', 'createdAt')).toBe('Joined ▼')
  })

  it('toggles sort direction and resets the current page', async () => {
    const state = useAdminUserListState()
    state.page.value = 3

    state.handleSort('loginId')
    await nextTick()

    expect(state.sort.value).toBe('loginId,asc')
    expect(state.page.value).toBe(0)
    expect(state.sortField.value).toBe('loginId')
    expect(state.sortDirection.value).toBe('asc')
    expect(state.getSortLabel('Login', 'loginId')).toBe('Login ▲')
    expect(state.getSortLabel('Joined', 'createdAt')).toBe('Joined')

    state.page.value = 2
    state.handleSort('loginId')
    await nextTick()

    expect(state.sort.value).toBe('loginId,desc')
    expect(state.page.value).toBe(0)
    expect(state.sortDirection.value).toBe('desc')
    expect(state.getSortLabel('Login', 'loginId')).toBe('Login ▼')
  })

  it('resets page when page size changes', async () => {
    const state = useAdminUserListState()
    state.page.value = 5

    state.size.value = 50
    await nextTick()

    expect(state.page.value).toBe(0)
    expect(state.params.value.size).toBe(50)
  })

  it('keeps filters and sort when only page changes', () => {
    const state = useAdminUserListState()
    state.filterForm.status = 'ACTIVE'
    state.applyFilters()
    state.handleSort('loginId')

    state.page.value = 2

    expect(state.params.value).toEqual(expect.objectContaining({
      page: 2,
      status: 'ACTIVE',
      sort: 'loginId,asc',
    }))
  })
})
