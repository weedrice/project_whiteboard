import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CommonCodeManagement from '../CommonCodeManagement.vue'
import { createDeferred } from '@/test/async'
import { configureAuthSessionEffects, useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { closeAuthBoundaryChannelForTest } from '@/api/authBoundaryChannel'
import { closeAuthRefreshCoordinatorForTest } from '@/api/authRefreshCoordinator'
import { clearStoredAuthTokens } from '@/utils/authTokenStorage'
import type { User } from '@/types'

vi.mock('@/api/auth', () => ({ authApi: {} }))
vi.mock('@/features/notifications/pushSubscriptions', () => ({
  detachBrowserPushSubscriptionForSession: vi.fn(async () => undefined),
}))

const commonCodeApiMock = vi.hoisted(() => ({
  getAll: vi.fn(),
  getDetails: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  createDetail: vi.fn(),
  getAllDetails: vi.fn(),
  updateDetail: vi.fn(),
  deleteDetail: vi.fn(),
}))

const navigationMocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  routeLeaveGuard: null as null | (() => Promise<boolean>),
}))

vi.mock('@/api/commonCode', () => ({ commonCodeApi: commonCodeApiMock }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: navigationMocks.confirm }) }))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    onBeforeRouteLeave: (guard: () => Promise<boolean>) => {
      navigationMocks.routeLeaveGuard = guard
    },
  }
})

const response = (data: unknown) => ({ data: { success: true, data } })
const sessionConfig = expect.objectContaining({
  expectedSession: expect.objectContaining({ sessionGeneration: expect.any(Number) }),
  signal: expect.any(AbortSignal),
})

describe('CommonCodeManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    navigationMocks.confirm.mockResolvedValue(true)
    navigationMocks.routeLeaveGuard = null
    commonCodeApiMock.getAll.mockResolvedValue(response([
      { typeCode: 'REPORT_REASON', typeName: 'Report reason', description: 'Reasons' },
      { typeCode: 'USER_STATUS', typeName: 'User status', description: 'Statuses' },
    ]))
    commonCodeApiMock.getAllDetails.mockResolvedValue(response([
      { id: 1, typeCode: 'REPORT_REASON', codeValue: 'SPAM', codeName: 'Spam', sortOrder: 1, isActive: true },
    ]))
    commonCodeApiMock.updateDetail.mockResolvedValue(response({}))
  })

  it('loads code groups and the selected group details', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    expect(commonCodeApiMock.getAll).toHaveBeenCalledOnce()
    expect(commonCodeApiMock.getAllDetails).toHaveBeenCalledWith('REPORT_REASON', expect.objectContaining({ signal: expect.any(AbortSignal), expectedSession: expect.objectContaining({ sessionGeneration: expect.any(Number) }) }))
    expect(wrapper.text()).toContain('Report reason')
    expect(wrapper.text()).toContain('Spam')
  })

  it('keeps the selected code editor intact after cancelling code creation', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    const masterInputs = wrapper.findAll('input')
    expect(masterInputs[0].element.value).toBe('REPORT_REASON')
    expect(masterInputs[1].element.value).toBe('Report reason')
    expect(masterInputs[2].element.value).toBe('Reasons')

    const addButton = wrapper.findAll('button').find((button) => button.text().includes('admin.commonCodes.addCode'))
    await addButton?.trigger('click')
    const cancelButton = wrapper.findAll('button').find((button) => button.text() === 'common.cancel')
    await cancelButton?.trigger('click')

    expect(wrapper.findAll('input')[0].element.value).toBe('REPORT_REASON')
    expect(wrapper.findAll('input')[1].element.value).toBe('Report reason')
    expect(wrapper.findAll('input')[2].element.value).toBe('Reasons')
  })

  it('keeps the current code selected when discarding dirty master changes is cancelled', async () => {
    navigationMocks.confirm.mockResolvedValueOnce(false)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    await wrapper.findAll('input')[1].setValue('Changed name')
    const otherCode = wrapper.findAll('button').find((button) => button.text().includes('USER_STATUS'))
    await otherCode?.trigger('click')
    await flushPromises()

    expect(navigationMocks.confirm).toHaveBeenCalledWith('admin.commonCodes.messages.confirmDiscardChanges')
    expect(wrapper.findAll('input')[0].element.value).toBe('REPORT_REASON')
    expect(wrapper.findAll('input')[1].element.value).toBe('Changed name')
    expect(commonCodeApiMock.getAllDetails).not.toHaveBeenCalledWith('USER_STATUS', expect.anything())
  })

  it('blocks route leave when dirty master changes are not confirmed', async () => {
    navigationMocks.confirm.mockResolvedValueOnce(false)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    await wrapper.findAll('input')[1].setValue('Changed name')

    await expect(navigationMocks.routeLeaveGuard?.()).resolves.toBe(false)
    expect(navigationMocks.confirm).toHaveBeenCalledWith('admin.commonCodes.messages.confirmDiscardChanges')
  })

  it('locks a create modal and route leave until its save request settles', async () => {
    const createRequest = createDeferred<unknown>()
    commonCodeApiMock.create.mockReturnValueOnce(createRequest.promise)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text().includes('admin.commonCodes.addCode'))?.trigger('click')
    const vm = wrapper.vm as unknown as {
      createCodeForm: { typeCode: string; typeName: string }
      saveCode: () => Promise<void>
    }
    Object.assign(vm.createCodeForm, { typeCode: 'NEW_CODE', typeName: 'New code' })
    const pendingSave = vm.saveCode()
    await vi.waitFor(() => expect(commonCodeApiMock.create).toHaveBeenCalledTimes(1))

    const lockedModal = wrapper.get('[role="dialog"]')
    expect(lockedModal.findAll('input')[0].attributes('disabled')).toBeDefined()
    expect(lockedModal.findAll('button').find((button) => button.text() === 'common.cancel')?.attributes('disabled')).toBeDefined()
    await expect(navigationMocks.routeLeaveGuard?.()).resolves.toBe(false)

    createRequest.resolve(response({ typeCode: 'NEW_CODE' }))
    await pendingSave
    await flushPromises()
    expect(wrapper.findAll('button').some((button) => button.text() === 'common.cancel')).toBe(false)
  })

  it('keeps existing code values read-only while allowing new detail values', async () => {
    commonCodeApiMock.createDetail.mockResolvedValue(response({}))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === 'common.edit')!.trigger('click')
    const editInputs = wrapper.get('[role="dialog"]').findAll('input')
    expect(editInputs[0].element.readOnly).toBe(true)
    expect(editInputs[0].element.value).toBe('SPAM')
    await editInputs[1].setValue('Updated spam label')
    await wrapper.get('[role="dialog"]').findAll('button').find((button) => button.text() === 'common.save')!.trigger('click')
    await flushPromises()
    expect(commonCodeApiMock.updateDetail).toHaveBeenCalledWith(1, expect.objectContaining({ codeValue: 'SPAM', codeName: 'Updated spam label' }), sessionConfig)

    await wrapper.findAll('button').find((button) => button.text().includes('admin.commonCodes.addDetail'))!.trigger('click')
    const createInputs = wrapper.get('[role="dialog"]').findAll('input')
    expect(createInputs[0].element.readOnly).toBe(false)
    await createInputs[0].setValue('NEW_REASON')
    await wrapper.get('[role="dialog"]').findAll('input')[1].setValue('New reason')
    await wrapper.get('[role="dialog"]').findAll('button').find((button) => button.text() === 'common.save')!.trigger('click')
    await flushPromises()
    expect(commonCodeApiMock.createDetail).toHaveBeenCalledWith('REPORT_REASON', expect.objectContaining({ codeValue: 'NEW_REASON' }), sessionConfig)
    wrapper.unmount()
    queryClient.clear()
  })

  it('shows inactive details and allows an administrator to reactivate them', async () => {
    commonCodeApiMock.getAllDetails.mockResolvedValue(response([
      { id: 2, typeCode: 'REPORT_REASON', codeValue: 'ABUSE', codeName: 'Abuse', sortOrder: 2, isActive: false },
    ]))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('common.inactive')
    const vm = wrapper.vm as unknown as {
      openDetailModal: (detailId: number) => void
      detailForm: { isActive: boolean }
      saveDetail: () => Promise<void>
    }
    vm.openDetailModal(2)
    vm.detailForm.isActive = true
    await vm.saveDetail()

    expect(commonCodeApiMock.updateDetail).toHaveBeenCalledWith(2, {
      codeValue: 'ABUSE',
      codeName: 'Abuse',
      sortOrder: 2,
      isActive: true,
    }, sessionConfig)
  })

  it('keeps the edited type fixed while saving and invalidates its public cache', async () => {
    const updateRequest = createDeferred<unknown>()
    commonCodeApiMock.updateDetail.mockReturnValueOnce(updateRequest.promise)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(['common-codes', 'REPORT_REASON', 'details'], [{ codeValue: 'SPAM' }])
    const wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      selectedTypeCode: string
      openDetailModal: (detailId: number) => void
      saveDetail: () => Promise<void>
      selectTypeCode: (typeCode: string) => Promise<void>
    }
    vm.openDetailModal(1)
    const pendingSave = vm.saveDetail()
    await vi.waitFor(() => expect(commonCodeApiMock.updateDetail).toHaveBeenCalledTimes(1))

    await vm.selectTypeCode('USER_STATUS')
    expect(vm.selectedTypeCode).toBe('REPORT_REASON')

    updateRequest.resolve(response({}))
    await pendingSave
    await flushPromises()

    expect(queryClient.getQueryState(['common-codes', 'REPORT_REASON', 'details'])?.isInvalidated)
      .toBe(true)
  })
})

type CommonCodeViewModel = {
  selectedTypeCode: string
  isSaving: boolean
  isCodeModalOpen: boolean
  isDetailModalOpen: boolean
  isMasterDirty: boolean
  createCodeForm: { typeCode: string; typeName: string }
  masterCodeForm: { typeCode: string; typeName: string }
  detailForm: { codeValue: string; codeName: string }
  openCodeModal: () => void
  openDetailModal: (detailId?: number) => void
  saveCode: () => Promise<void>
  saveDetail: () => Promise<void>
  deleteDetail: (detailId: number) => Promise<void>
  selectTypeCode: (typeCode: string) => Promise<void>
}

describe('CommonCodeManagement session-scoped actions', () => {
  let pinia: ReturnType<typeof createPinia>
  let authStore: ReturnType<typeof useAuthStore>
  let queryClient: QueryClient
  let wrapper: VueWrapper | undefined
  const publicDetailsKey = ['common-codes', 'REPORT_REASON', 'details']
  const user = (userId: number): User => ({
    userId, loginId: `admin-${userId}`, displayName: `Admin ${userId}`,
    email: `admin-${userId}@example.test`, status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z',
  })

  function switchAccount() {
    authStore.setTokens('token-b')
    authStore.user = user(2)
  }

  async function setup() {
    wrapper = mount(CommonCodeManagement, {
      global: {
        plugins: [pinia, [VueQueryPlugin, { queryClient }]],
        mocks: { $t: (key: string) => key },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()
    return wrapper.vm as unknown as CommonCodeViewModel
  }

  beforeEach(() => {
    vi.resetAllMocks()
    navigationMocks.confirm.mockResolvedValue(true)
    navigationMocks.routeLeaveGuard = null
    commonCodeApiMock.getAll.mockResolvedValue(response([
      { typeCode: 'REPORT_REASON', typeName: 'Report reason', description: 'Reasons' },
      { typeCode: 'USER_STATUS', typeName: 'User status', description: 'Statuses' },
    ]))
    commonCodeApiMock.getAllDetails.mockResolvedValue(response([
      { id: 1, typeCode: 'REPORT_REASON', codeValue: 'SPAM', codeName: 'Spam', sortOrder: 1, isActive: true },
    ]))
    for (const method of ['create', 'update', 'createDetail', 'updateDetail', 'deleteDetail'] as const) {
      commonCodeApiMock[method].mockResolvedValue(response({}))
    }
    pinia = createPinia()
    setActivePinia(pinia)
    authStore = useAuthStore(pinia)
    configureAuthQueryScope(() => authStore.sessionGeneration)
    configureAuthSessionEffects({ onSessionBoundary: notifyAuthSessionBoundary })
    authStore.setTokens('token-a')
    authStore.user = user(1)
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(publicDetailsKey, [{ codeValue: 'SPAM' }])
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    queryClient.clear()
    disposePinia(pinia)
    configureAuthSessionEffects({})
    configureAuthQueryScope(() => 0)
    closeAuthBoundaryChannelForTest()
    closeAuthRefreshCoordinatorForTest()
    clearStoredAuthTokens(false)
    vi.restoreAllMocks()
  })

  it('passes the initiating session to all five writes and preserves normal completion', async () => {
    const vm = await setup()
    const expectedConfig = {
      expectedSession: { sessionGeneration: authStore.sessionGeneration, userId: 1 },
      signal: expect.any(AbortSignal),
    }
    await vm.saveCode()
    expect(commonCodeApiMock.update).toHaveBeenCalledWith('REPORT_REASON', expect.any(Object), expectedConfig)

    vm.openCodeModal()
    Object.assign(vm.createCodeForm, { typeCode: 'NEW_CODE', typeName: 'New code' })
    await vm.saveCode()
    expect(commonCodeApiMock.create).toHaveBeenCalledWith(expect.objectContaining({ typeCode: 'NEW_CODE' }), expectedConfig)
    expect(vm.isCodeModalOpen).toBe(false)
    await flushPromises()
    await vm.selectTypeCode('REPORT_REASON')
    await flushPromises()

    vm.openDetailModal()
    Object.assign(vm.detailForm, { codeValue: 'NEW', codeName: 'New detail' })
    await vm.saveDetail()
    expect(commonCodeApiMock.createDetail).toHaveBeenCalledWith('REPORT_REASON', expect.any(Object), expectedConfig)
    expect(vm.isDetailModalOpen).toBe(false)

    vm.openDetailModal(1)
    await vm.saveDetail()
    expect(commonCodeApiMock.updateDetail).toHaveBeenCalledWith(1, expect.any(Object), expectedConfig)
    await vm.deleteDetail(1)
    expect(commonCodeApiMock.deleteDetail).toHaveBeenCalledWith(1, expectedConfig)
    expect(vm.isSaving).toBe(false)
    expect(queryClient.getQueryState(publicDetailsKey)?.isInvalidated).toBe(true)
    expect(useToastStore(pinia).toasts.some(toast => toast.message === 'admin.commonCodes.messages.deleted')).toBe(true)
  })

  it('does not delete after switching accounts while confirmation is pending', async () => {
    const confirmation = createDeferred<boolean>()
    navigationMocks.confirm.mockReturnValueOnce(confirmation.promise)
    const vm = await setup()
    const pendingDelete = vm.deleteDetail(1)
    switchAccount()
    await flushPromises()
    confirmation.resolve(true)
    await expect(pendingDelete).resolves.toBeUndefined()
    expect(commonCodeApiMock.deleteDetail).not.toHaveBeenCalled()
    expect(queryClient.getQueryState(publicDetailsKey)?.isInvalidated).toBe(false)
    expect(useToastStore(pinia).toasts).toHaveLength(0)
  })

  it('does not delete the old group when its selection changes during confirmation', async () => {
    const confirmation = createDeferred<boolean>()
    navigationMocks.confirm.mockReturnValueOnce(confirmation.promise)
    const vm = await setup()
    const pendingDelete = vm.deleteDetail(1)
    await vm.selectTypeCode('USER_STATUS')
    confirmation.resolve(true)
    await pendingDelete
    expect(commonCodeApiMock.deleteDetail).not.toHaveBeenCalled()
    expect(vm.selectedTypeCode).toBe('USER_STATUS')
  })

  it.each(['resolve', 'reject'] as const)('does not let a late A %s or finally alter B pending save', async (settlement) => {
    const oldRequest = createDeferred<unknown>()
    const newRequest = createDeferred<unknown>()
    commonCodeApiMock.updateDetail.mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(newRequest.promise)
    const vm = await setup()
    vm.openDetailModal(1)
    const pendingOldSave = vm.saveDetail()
    const oldConfig = commonCodeApiMock.updateDetail.mock.calls[0][2]
    switchAccount()
    await flushPromises()
    expect(oldConfig.signal.aborted).toBe(true)
    vm.openDetailModal(1)
    const pendingNewSave = vm.saveDetail()
    expect(vm.isSaving).toBe(true)
    if (settlement === 'resolve') oldRequest.resolve(response({}))
    else oldRequest.reject(new Error('old account failure'))
    await expect(pendingOldSave).resolves.toBeUndefined()
    expect(vm.isSaving).toBe(true)
    expect(vm.isDetailModalOpen).toBe(true)
    expect(queryClient.getQueryState(publicDetailsKey)?.isInvalidated).toBe(false)
    expect(useToastStore(pinia).toasts).toHaveLength(0)
    newRequest.resolve(response({}))
    await pendingNewSave
    expect(vm.isSaving).toBe(false)
    expect(vm.isDetailModalOpen).toBe(false)
  })

  it.each(['save', 'delete'] as const)('stops %s cache invalidation and UI effects after switching during a detail refetch', async (operation) => {
    const refetch = createDeferred<unknown>()
    const vm = await setup()
    commonCodeApiMock.getAllDetails.mockReturnValueOnce(refetch.promise)
    vm.openDetailModal(1)
    const pending = operation === 'save' ? vm.saveDetail() : vm.deleteDetail(1)
    await vi.waitFor(() => expect(commonCodeApiMock.getAllDetails).toHaveBeenCalledTimes(2))
    switchAccount()
    await flushPromises()
    vm.openDetailModal(1)
    vm.detailForm.codeName = 'B draft'
    refetch.resolve(response([{ id: 1, typeCode: 'REPORT_REASON', codeValue: 'SPAM', codeName: 'A stale result', sortOrder: 1, isActive: true }]))
    await expect(pending).resolves.toBeUndefined()
    await flushPromises()
    expect(queryClient.getQueryState(publicDetailsKey)?.isInvalidated).toBe(false)
    expect(vm.isDetailModalOpen).toBe(true)
    expect(vm.detailForm.codeName).toBe('B draft')
    expect(useToastStore(pinia).toasts).toHaveLength(0)
  })

  it('does not close a new account modal after a master refetch finishes', async () => {
    const refetch = createDeferred<unknown>()
    const vm = await setup()
    commonCodeApiMock.getAll.mockReturnValueOnce(refetch.promise)
    vm.openCodeModal()
    Object.assign(vm.createCodeForm, { typeCode: 'A_CODE', typeName: 'A code' })
    const pendingSave = vm.saveCode()
    await vi.waitFor(() => expect(commonCodeApiMock.getAll).toHaveBeenCalledTimes(2))
    switchAccount()
    await flushPromises()
    await vm.selectTypeCode('USER_STATUS')
    vm.openCodeModal()
    Object.assign(vm.createCodeForm, { typeCode: 'B_CODE', typeName: 'B code' })
    refetch.resolve(response([{ typeCode: 'A_CODE', typeName: 'A code' }]))
    await expect(pendingSave).resolves.toBeUndefined()
    expect(vm.isCodeModalOpen).toBe(true)
    expect(vm.selectedTypeCode).toBe('USER_STATUS')
    expect(vm.createCodeForm.typeCode).toBe('B_CODE')
    expect(useToastStore(pinia).toasts).toHaveLength(0)
  })

  it('preserves current-session errors while releasing the save lock', async () => {
    const error = new Error('save failed')
    commonCodeApiMock.updateDetail.mockRejectedValueOnce(error)
    const vm = await setup()
    vm.openDetailModal(1)
    await expect(vm.saveDetail()).rejects.toBe(error)
    expect(vm.isSaving).toBe(false)
    expect(vm.isDetailModalOpen).toBe(true)
    expect(queryClient.getQueryState(publicDetailsKey)?.isInvalidated).toBe(false)
    expect(useToastStore(pinia).toasts).toHaveLength(0)
  })
})
