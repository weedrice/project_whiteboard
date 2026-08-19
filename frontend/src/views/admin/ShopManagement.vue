<script setup lang="ts">
import { computed, ref } from 'vue'
import { PauseCircle, PlayCircle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFilterActions from '@/components/admin/AdminFilterActions.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import AdminTableActions from '@/components/admin/AdminTableActions.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import {
  useAdminShopItems,
  useUpdateAdminShopItemSaleStatus,
} from '@/features/admin/shop/useAdminShopItems'
import { useToastStore } from '@/stores/toast'
import type { AdminShopItem, AdminShopItemSearchParams } from '@/types'
import { formatAdminPaginationSummary } from '@/utils/adminPaginationSummary'
import { formatDateTimeOrDash } from '@/utils/date'
import { extractErrorMessage } from '@/utils/errorHandler'
import { optionalTrimmedText } from '@/utils/inputNormalization'
import { formatInteger } from '@/utils/numberFormat'

const { t } = useI18n()
const toastStore = useToastStore()

const searchText = ref('')
const itemType = ref('')
const sourceStatus = ref('')
const saleStatus = ref('')
const appliedFilters = ref<Omit<AdminShopItemSearchParams, 'page' | 'size'>>({})
const { page, params, resetPage } = usePaginatedQueryState({
  initialSize: 20,
  extraParams: appliedFilters,
})
const { data, isLoading } = useAdminShopItems(params)
const { items, totalElements, totalPages } = usePageResponseState(data, page)
const updateMutation = useUpdateAdminShopItemSaleStatus()

const selectedItem = ref<AdminShopItem | null>(null)
const reason = ref('')
const reasonError = ref('')

const columns = computed<TableColumn[]>(() => [
  { key: 'itemId', label: t('admin.shop.table.id'), width: '8%' },
  { key: 'itemName', label: t('admin.shop.table.name'), width: '23%' },
  { key: 'itemType', label: t('admin.shop.table.type'), width: '12%' },
  { key: 'price', label: t('admin.shop.table.price'), width: '12%' },
  { key: 'sourceStatus', label: t('admin.shop.table.sourceStatus'), width: '13%' },
  { key: 'saleStatus', label: t('admin.shop.table.saleStatus'), width: '14%' },
  { key: 'modifiedAt', label: t('admin.shop.table.modifiedAt'), width: '12%', hideBelow: 'lg' },
  { key: 'actions', label: t('admin.shop.table.actions'), align: 'right', width: '12%' },
])

const booleanOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  { value: 'true', label: t('admin.shop.filter.enabled') },
  { value: 'false', label: t('admin.shop.filter.disabled') },
])

function toOptionalBoolean(value: string): boolean | undefined {
  if (value === 'true') return true
  if (value === 'false') return false
  return undefined
}

function handleSearch() {
  appliedFilters.value = {
    q: optionalTrimmedText(searchText.value),
    itemType: optionalTrimmedText(itemType.value),
    isActive: toOptionalBoolean(sourceStatus.value),
    isSaleEnabled: toOptionalBoolean(saleStatus.value),
  }
  resetPage()
}

function resetFilters() {
  searchText.value = ''
  itemType.value = ''
  sourceStatus.value = ''
  saleStatus.value = ''
  handleSearch()
}

function saleStatusPresentation(item: AdminShopItem) {
  if (!item.isActive && item.targetId === null) {
    return { label: t('admin.shop.status.retired'), variant: 'gray' as const }
  }
  if (!item.isSaleEnabled) {
    return { label: t('admin.shop.status.suspended'), variant: 'danger' as const }
  }
  if (!item.isActive) {
    return { label: t('admin.shop.status.sourceInactive'), variant: 'warning' as const }
  }
  return { label: t('admin.shop.status.onSale'), variant: 'success' as const }
}

function openSaleStatusModal(item: AdminShopItem) {
  selectedItem.value = item
  reason.value = ''
  reasonError.value = ''
}

function closeSaleStatusModal() {
  if (updateMutation.isPending.value) return
  selectedItem.value = null
  reason.value = ''
  reasonError.value = ''
}

async function submitSaleStatus() {
  const item = selectedItem.value
  if (!item) return
  const normalizedReason = reason.value.trim()
  if (!normalizedReason) {
    reasonError.value = t('admin.shop.messages.reasonRequired')
    return
  }
  if (normalizedReason.length > 500) {
    reasonError.value = t('admin.shop.messages.reasonTooLong')
    return
  }

  reasonError.value = ''
  const nextSaleEnabled = !item.isSaleEnabled
  try {
    await updateMutation.mutateAsync({
      itemId: item.itemId,
      saleEnabled: nextSaleEnabled,
      reason: normalizedReason,
    })
    toastStore.addToast(
      nextSaleEnabled
        ? t('admin.shop.messages.resumed')
        : t('admin.shop.messages.suspended'),
      'success',
    )
    closeSaleStatusModal()
  } catch (error) {
    toastStore.addToast(
      extractErrorMessage(error) || t('admin.shop.messages.updateFailed'),
      'error',
    )
  }
}
</script>

<template>
  <AdminDataPage :title="t('admin.shop.title')" :description="t('admin.shop.description')">
    <template #filters>
      <AdminFilterPanel class="mt-4" :title="t('admin.shop.filter.title')">
        <form class="flex flex-wrap items-end gap-3" @submit.prevent="handleSearch">
          <AdminFilterField :label="t('admin.shop.filter.search')" for-id="shop-item-search" width="search">
            <BaseInput
              id="shop-item-search"
              v-model="searchText"
              maxlength="100"
              :placeholder="t('admin.shop.filter.searchPlaceholder')"
            />
          </AdminFilterField>
          <AdminFilterField :label="t('admin.shop.filter.itemType')" for-id="shop-item-type" width="compact">
            <BaseInput id="shop-item-type" v-model="itemType" maxlength="50" placeholder="EMOTICON" />
          </AdminFilterField>
          <AdminFilterField :label="t('admin.shop.filter.sourceStatus')" for-id="shop-source-status" width="select">
            <BaseSelect id="shop-source-status" v-model="sourceStatus" :options="booleanOptions" />
          </AdminFilterField>
          <AdminFilterField :label="t('admin.shop.filter.saleStatus')" for-id="shop-sale-status" width="select">
            <BaseSelect id="shop-sale-status" v-model="saleStatus" :options="booleanOptions" />
          </AdminFilterField>
          <AdminFilterActions @search="handleSearch" @reset="resetFilters" />
        </form>
      </AdminFilterPanel>
    </template>

    <AdminPaginatedTable
      table-class="mt-4 overflow-x-auto rounded-lg border border-[var(--nv-line)] bg-[var(--nv-surface)]"
      :columns="columns"
      :caption="t('admin.shop.title')"
      :items="items"
      :loading="isLoading"
      :empty-text="t('admin.shop.empty')"
      row-key="itemId"
      :page="page"
      :total-pages="totalPages"
      :summary="formatAdminPaginationSummary(totalElements, { page, totalPages, t })"
      @page-change="page = $event"
    >
      <template #cell-itemId="{ item }">
        <span class="font-mono text-xs">#{{ item.itemId }}</span>
      </template>
      <template #cell-itemName="{ item }">
        <div class="min-w-0">
          <p class="truncate font-medium nv-title" :title="item.itemName">{{ item.itemName }}</p>
          <p v-if="item.targetId !== null" class="text-xs nv-text-subtle">target #{{ item.targetId }}</p>
        </div>
      </template>
      <template #cell-itemType="{ item }">
        <span class="font-mono text-xs">{{ item.itemType }}</span>
      </template>
      <template #cell-price="{ item }">
        {{ formatInteger(item.price) }} P
      </template>
      <template #cell-sourceStatus="{ item }">
        <AdminStatusBadge
          :label="item.isActive ? t('admin.shop.status.active') : t('admin.shop.status.inactive')"
          :variant="item.isActive ? 'success' : 'gray'"
        />
      </template>
      <template #cell-saleStatus="{ item }">
        <AdminStatusBadge
          :label="saleStatusPresentation(item).label"
          :variant="saleStatusPresentation(item).variant"
        />
      </template>
      <template #cell-modifiedAt="{ item }">
        <span class="text-xs">{{ formatDateTimeOrDash(item.modifiedAt) }}</span>
      </template>
      <template #cell-actions="{ item }">
        <AdminTableActions>
          <AdminActionButton
            v-if="item.targetId !== null"
            :label="item.isSaleEnabled ? t('admin.shop.actions.suspend') : t('admin.shop.actions.resume')"
            :tone="item.isSaleEnabled ? 'danger' : 'success'"
            :disabled="updateMutation.isPending.value"
            @click="openSaleStatusModal(item)"
          >
            <PauseCircle v-if="item.isSaleEnabled" class="mr-1 h-4 w-4" aria-hidden="true" />
            <PlayCircle v-else class="mr-1 h-4 w-4" aria-hidden="true" />
            {{ item.isSaleEnabled ? t('admin.shop.actions.suspend') : t('admin.shop.actions.resume') }}
          </AdminActionButton>
        </AdminTableActions>
      </template>
    </AdminPaginatedTable>

    <BaseModal
      :is-open="selectedItem !== null"
      :title="selectedItem?.isSaleEnabled ? t('admin.shop.modal.suspendTitle') : t('admin.shop.modal.resumeTitle')"
      :close-on-backdrop="!updateMutation.isPending.value"
      :close-on-escape="!updateMutation.isPending.value"
      @close="closeSaleStatusModal"
    >
      <p class="mb-4 text-sm nv-text-muted">
        {{ t('admin.shop.modal.description', { name: selectedItem?.itemName ?? '' }) }}
      </p>
      <BaseTextarea
        id="shop-sale-status-reason"
        v-model="reason"
        :label="t('admin.shop.modal.reason')"
        :placeholder="t('admin.shop.modal.reasonPlaceholder')"
        :error="reasonError"
        :disabled="updateMutation.isPending.value"
        maxlength="500"
        rows="4"
      />
      <template #footer>
        <BaseButton variant="secondary" :disabled="updateMutation.isPending.value" @click="closeSaleStatusModal">
          {{ t('common.cancel') }}
        </BaseButton>
        <BaseButton
          :variant="selectedItem?.isSaleEnabled ? 'danger' : 'primary'"
          :loading="updateMutation.isPending.value"
          @click="submitSaleStatus"
        >
          {{ selectedItem?.isSaleEnabled ? t('admin.shop.actions.suspend') : t('admin.shop.actions.resume') }}
        </BaseButton>
      </template>
    </BaseModal>
  </AdminDataPage>
</template>
