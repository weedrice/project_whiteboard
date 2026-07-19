<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus, Save, Trash2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminModalActions from '@/components/admin/AdminModalActions.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { commonCodeApi, type CommonCodeDetailPayload, type CommonCodePayload } from '@/api/commonCode'
import { useAdminDataQuery } from '@/features/admin/queries/adminApiQuery'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'

const { t } = useI18n()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const selectedTypeCode = ref('')
const isCodeModalOpen = ref(false)
const isDetailModalOpen = ref(false)
const editingDetailId = ref<number | null>(null)
const isSaving = ref(false)

type CommonCodeForm = Omit<CommonCodePayload, 'description'> & { description: string }
const emptyCodeForm = (): CommonCodeForm => ({ typeCode: '', typeName: '', description: '' })
const emptyDetailForm = (): CommonCodeDetailPayload => ({
  codeValue: '', codeName: '', sortOrder: 0, isActive: true,
})
const codeForm = reactive<CommonCodeForm>(emptyCodeForm())
const detailForm = reactive<CommonCodeDetailPayload>(emptyDetailForm())

const codesQuery = useAdminDataQuery(['admin', 'common-codes'], (config) => commonCodeApi.getAll(config))
const codes = computed(() => codesQuery.data.value ?? [])
const selectedCode = computed(() => codes.value.find((code) => code.typeCode === selectedTypeCode.value) ?? null)
const detailsQuery = useAdminDataQuery(
  computed(() => ['admin', 'common-codes', selectedTypeCode.value, 'details']),
  (config) => commonCodeApi.getDetails(selectedTypeCode.value, config),
  { enabled: computed(() => Boolean(selectedTypeCode.value)) },
)
const details = computed(() => detailsQuery.data.value ?? [])

watch(codes, (value) => {
  if (!value.length) selectedTypeCode.value = ''
  else if (!value.some((code) => code.typeCode === selectedTypeCode.value)) selectedTypeCode.value = value[0].typeCode
}, { immediate: true })

watch(selectedCode, (value) => {
  if (value) Object.assign(codeForm, { typeCode: value.typeCode, typeName: value.typeName, description: value.description ?? '' })
}, { immediate: true })

function resetTransientState() {
  isCodeModalOpen.value = false
  isDetailModalOpen.value = false
  editingDetailId.value = null
  isSaving.value = false
  Object.assign(detailForm, emptyDetailForm())
}
watch(() => authStore.sessionGeneration, resetTransientState, { flush: 'sync' })

const validCode = (value: CommonCodePayload) => value.typeCode.trim().length > 0
  && value.typeCode.trim().length <= 50 && value.typeName.trim().length > 0
  && value.typeName.trim().length <= 100 && (value.description?.trim().length ?? 0) <= 255
const validDetail = (value: CommonCodeDetailPayload) => value.codeValue.trim().length > 0
  && value.codeValue.trim().length <= 100 && value.codeName.trim().length > 0
  && value.codeName.trim().length <= 100 && Number.isInteger(Number(value.sortOrder))
const codePayload = (): CommonCodePayload => ({
  typeCode: codeForm.typeCode.trim(), typeName: codeForm.typeName.trim(), description: codeForm.description?.trim() || null,
})
const detailPayload = (): CommonCodeDetailPayload => ({
  codeValue: detailForm.codeValue.trim(), codeName: detailForm.codeName.trim(),
  sortOrder: Number(detailForm.sortOrder), isActive: Boolean(detailForm.isActive),
})

function openCodeModal() {
  Object.assign(codeForm, emptyCodeForm())
  isCodeModalOpen.value = true
}
function closeCodeModal() {
  isCodeModalOpen.value = false
  isSaving.value = false
}
function openDetailModal(detailId?: number) {
  const detail = detailId == null ? null : details.value.find((item) => item.id === detailId)
  editingDetailId.value = detail?.id ?? null
  Object.assign(detailForm, detail ? {
    codeValue: detail.codeValue, codeName: detail.codeName, sortOrder: detail.sortOrder, isActive: detail.isActive,
  } : emptyDetailForm())
  isDetailModalOpen.value = true
}
function closeDetailModal() {
  isDetailModalOpen.value = false
  editingDetailId.value = null
  isSaving.value = false
}

async function saveCode() {
  if (isSaving.value || !validCode(codeForm)) {
    if (!isSaving.value) toastStore.addToast(t('admin.commonCodes.messages.invalid'), 'warning')
    return
  }
  const intent = captureAuthSessionIntent(authStore)
  const payload = codePayload()
  const creating = isCodeModalOpen.value
  isSaving.value = true
  try {
    if (creating) await commonCodeApi.create(payload)
    else await commonCodeApi.update(payload.typeCode, payload)
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    await codesQuery.refetch()
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    selectedTypeCode.value = payload.typeCode
    toastStore.addToast(t('admin.commonCodes.messages.saved'), 'success')
    if (creating) closeCodeModal()
  } finally {
    if (isAuthSessionIntentCurrent(authStore, intent)) isSaving.value = false
  }
}

async function saveDetail() {
  if (!selectedTypeCode.value || isSaving.value || !validDetail(detailForm)) {
    if (!isSaving.value) toastStore.addToast(t('admin.commonCodes.messages.invalid'), 'warning')
    return
  }
  const intent = captureAuthSessionIntent(authStore)
  const payload = detailPayload()
  const detailId = editingDetailId.value
  isSaving.value = true
  try {
    if (detailId == null) await commonCodeApi.createDetail(selectedTypeCode.value, payload)
    else await commonCodeApi.updateDetail(detailId, payload)
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    await detailsQuery.refetch()
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    toastStore.addToast(t('admin.commonCodes.messages.detailSaved'), 'success')
    closeDetailModal()
  } finally {
    if (isAuthSessionIntentCurrent(authStore, intent)) isSaving.value = false
  }
}

async function deleteDetail(detailId: number) {
  const intent = captureAuthSessionIntent(authStore)
  if (!(await confirm(t('admin.commonCodes.messages.confirmDelete')))) return
  if (!isAuthSessionIntentCurrent(authStore, intent)) return
  await commonCodeApi.deleteDetail(detailId)
  if (!isAuthSessionIntentCurrent(authStore, intent)) return
  await detailsQuery.refetch()
  if (!isAuthSessionIntentCurrent(authStore, intent)) return
  toastStore.addToast(t('admin.commonCodes.messages.deleted'), 'success')
}
</script>

<template>
  <AdminDataPage :title="t('admin.commonCodes.title')" :description="t('admin.commonCodes.description')">
    <template #actions>
      <BaseButton @click="openCodeModal"><Plus class="mr-2 h-4 w-4" />{{ t('admin.commonCodes.addCode') }}</BaseButton>
    </template>
    <ErrorState v-if="codesQuery.isError.value" :message="t('common.messages.loadFailed')" show-retry @retry="codesQuery.refetch()" />
    <div v-else class="mt-8 grid gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <aside class="rounded-lg border border-[var(--nv-line)] bg-[var(--nv-surface)] p-3">
        <p v-if="codesQuery.isLoading.value" class="p-3 text-sm nv-text-subtle">{{ t('common.loading') }}</p>
        <p v-else-if="!codes.length" class="p-3 text-sm nv-text-subtle">{{ t('common.noData') }}</p>
        <button v-for="code in codes" :key="code.typeCode" type="button"
          class="mb-1 w-full rounded-md px-3 py-3 text-left nv-focus-ring"
          :class="selectedTypeCode === code.typeCode ? 'bg-[var(--nv-surface-2)] nv-title' : 'nv-text nv-hover-surface'"
          @click="selectedTypeCode = code.typeCode">
          <span class="block font-semibold">{{ code.typeName }}</span>
          <span class="block text-xs nv-text-subtle">{{ code.typeCode }}</span>
        </button>
      </aside>
      <section v-if="selectedCode" class="space-y-6">
        <div class="rounded-lg border border-[var(--nv-line)] bg-[var(--nv-surface)] p-5">
          <h2 class="mb-4 text-lg font-semibold nv-title">{{ t('admin.commonCodes.master') }}</h2>
          <div class="grid gap-4 sm:grid-cols-2">
            <BaseInput v-model="codeForm.typeCode" :label="t('admin.commonCodes.typeCode')" disabled />
            <BaseInput v-model="codeForm.typeName" :label="t('admin.commonCodes.typeName')" maxlength="100" />
            <BaseInput v-model="codeForm.description" :label="t('common.description')" maxlength="255" class="sm:col-span-2" />
          </div>
          <div class="mt-4 flex justify-end"><BaseButton :disabled="isSaving" @click="saveCode"><Save class="mr-2 h-4 w-4" />{{ t('common.save') }}</BaseButton></div>
        </div>
        <div class="rounded-lg border border-[var(--nv-line)] bg-[var(--nv-surface)] p-5">
          <div class="mb-4 flex items-center justify-between gap-3">
            <h2 class="text-lg font-semibold nv-title">{{ t('admin.commonCodes.details') }}</h2>
            <BaseButton size="sm" @click="openDetailModal()">{{ t('admin.commonCodes.addDetail') }}</BaseButton>
          </div>
          <p v-if="detailsQuery.isLoading.value" class="py-6 text-center text-sm nv-text-subtle">{{ t('common.loading') }}</p>
          <ErrorState v-else-if="detailsQuery.isError.value" :message="t('common.messages.loadFailed')" show-retry @retry="detailsQuery.refetch()" />
          <p v-else-if="!details.length" class="py-6 text-center text-sm nv-text-subtle">{{ t('common.noData') }}</p>
          <ul v-else class="divide-y divide-[var(--nv-line)]">
            <li v-for="detail in details" :key="detail.id" class="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div><p class="font-medium nv-title">{{ detail.codeName }} <span class="text-sm nv-text-subtle">({{ detail.codeValue }})</span></p>
                <p class="text-xs nv-text-subtle">{{ t('common.sortOrder') }}: {{ detail.sortOrder }} · {{ detail.isActive ? t('common.active') : t('common.inactive') }}</p></div>
              <div class="flex gap-2"><BaseButton size="sm" variant="secondary" @click="openDetailModal(detail.id)">{{ t('common.edit') }}</BaseButton>
                <BaseButton size="sm" variant="danger" @click="deleteDetail(detail.id)"><Trash2 class="mr-1 h-4 w-4" />{{ t('common.delete') }}</BaseButton></div>
            </li>
          </ul>
        </div>
      </section>
    </div>

    <BaseModal :is-open="isCodeModalOpen" :title="t('admin.commonCodes.addCode')" @close="closeCodeModal">
      <div class="space-y-4"><BaseInput v-model="codeForm.typeCode" :label="t('admin.commonCodes.typeCode')" maxlength="50" />
        <BaseInput v-model="codeForm.typeName" :label="t('admin.commonCodes.typeName')" maxlength="100" />
        <BaseInput v-model="codeForm.description" :label="t('common.description')" maxlength="255" /></div>
      <template #footer><AdminModalActions><BaseButton variant="secondary" @click="closeCodeModal">{{ t('common.cancel') }}</BaseButton>
        <BaseButton :disabled="isSaving" @click="saveCode">{{ t('common.save') }}</BaseButton></AdminModalActions></template>
    </BaseModal>
    <BaseModal :is-open="isDetailModalOpen" :title="editingDetailId == null ? t('admin.commonCodes.addDetail') : t('admin.commonCodes.editDetail')" @close="closeDetailModal">
      <div class="space-y-4"><BaseInput v-model="detailForm.codeValue" :label="t('admin.commonCodes.codeValue')" maxlength="100" />
        <BaseInput v-model="detailForm.codeName" :label="t('admin.commonCodes.codeName')" maxlength="100" />
        <BaseInput v-model="detailForm.sortOrder" :label="t('common.sortOrder')" type="number" />
        <BaseCheckbox v-model="detailForm.isActive" :label="t('common.active')" /></div>
      <template #footer><AdminModalActions><BaseButton variant="secondary" @click="closeDetailModal">{{ t('common.cancel') }}</BaseButton>
        <BaseButton :disabled="isSaving" @click="saveDetail">{{ t('common.save') }}</BaseButton></AdminModalActions></template>
    </BaseModal>
  </AdminDataPage>
</template>
