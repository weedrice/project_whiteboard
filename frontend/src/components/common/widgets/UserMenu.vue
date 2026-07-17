<template>
  <div class="nv-user-menu-root relative inline-block max-w-full text-left">
    <div class="min-w-0 max-w-full">
      <button
        :id="menuButtonId"
        ref="buttonRef"
        type="button"
        class="nv-user-menu-button nv-focus-ring inline-flex max-w-full min-w-0 items-center justify-start rounded-md font-medium nv-text-muted focus:outline-none"
        :class="[
          { 'cursor-default': isMenuDisabled },
          size === 'xs' ? 'text-xs' : size === 'inherit' ? 'text-inherit' : 'text-sm'
        ]"
        :title="displayName"
        :aria-haspopup="!isMenuDisabled"
        :aria-expanded="isDropdownOpen"
        :aria-controls="isDropdownOpen ? menuDropdownId : undefined"
        :disabled="isMenuDisabled"
        @click="toggleDropdown"
        @keydown="handleButtonKeyDown"
      >
        <span class="block truncate">{{ buttonLabel }}</span>
      </button>
    </div>

    <Teleport to="body">
      <div
        v-if="isDropdownOpen"
        :id="menuDropdownId"
        ref="dropdownRef"
        :style="dropdownStyle"
        class="absolute z-50 w-56 rounded-md nv-surface border nv-border shadow-lg transition-colors duration-200 focus:outline-none"
        role="menu"
        aria-orientation="vertical"
        :aria-labelledby="menuButtonId"
        @keydown="handleMenuKeyDown"
      >
        <div class="py-1" role="none">
          <button
            v-for="(item, index) in menuItems"
            :key="index"
            type="button"
            role="menuitem"
            :tabindex="index === selectedIndex ? 0 : -1"
            :class="[
              'block w-full px-4 py-2 text-left text-sm nv-text-muted',
              index === selectedIndex
                ? 'nv-active-surface'
                : 'nv-hover-surface'
            ]"
            @click="item.action"
            @mouseenter="setSelectedIndex(index)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
    </Teleport>

    <MessageModal :isOpen="isMessageModalOpen" :userId="userId" :displayName="displayName" @close="closeMessageModal" />
    <ReportModal
      :isOpen="isReportModalOpen"
      :targetText="displayName"
      :submit="submitUserReport"
      @close="closeReportModal"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, getCurrentInstance, nextTick, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useEventListener } from '@/composables/useEventListener'
import { useUserMenuActions } from '@/features/user/menu/useUserMenuActions'
import { useUserMenuPosition } from '@/features/user/menu/useUserMenuPosition'
import MessageModal from '@/components/user/MessageModal.vue'
import ReportModal from '@/components/report/ReportModal.vue'
import { reportApi } from '@/api/report'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { formatUserDisplayName } from '@/utils/userDisplay'

const { t } = useI18n()
const toastStore = useToastStore()
const router = useRouter()

const props = withDefaults(defineProps<{
  userId: number
  displayName: string
  buttonLabel?: string
  maxLabelLength?: number
  size?: 'default' | 'xs' | 'inherit'
}>(), {
  buttonLabel: undefined,
  maxLabelLength: undefined,
  size: 'default'
})

const isDropdownOpen = ref(false)
const menuInstanceId = `user-menu-${getCurrentInstance()?.uid ?? props.userId}`
const menuButtonId = `${menuInstanceId}-button`
const menuDropdownId = `${menuInstanceId}-dropdown`

const buttonLabel = computed(() => (
  props.buttonLabel
    ? props.buttonLabel
    : formatUserDisplayName(props.displayName, props.maxLabelLength, t('user.deletedUser'))
))

const buttonRef = ref<HTMLButtonElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)
const selectedIndex = ref(-1)

const { dropdownStyle, updateDropdownPosition } = useUserMenuPosition(buttonRef, dropdownRef, isDropdownOpen)
const {
  isMessageModalOpen,
  isReportModalOpen,
  isMenuDisabled,
  menuItems,
  closeMessageModal,
  closeReportModal
} = useUserMenuActions({
  userId: toRef(props, 'userId'),
  displayName: toRef(props, 'displayName'),
  closeDropdown,
  openProfile: () => router.push(`/user/${props.userId}`),
  t
})

const getMenuItemElements = () => {
  const renderedItems = Array.from(
    dropdownRef.value?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [],
  )
  if (renderedItems.length > 0) return renderedItems
  return Array.from(
    document.getElementById(menuDropdownId)?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [],
  )
}

const setSelectedIndex = (index: number) => {
  selectedIndex.value = index
}

const focusMenuItem = (index: number) => {
  const items = getMenuItemElements()
  if (items.length === 0) return
  const normalizedIndex = (index + items.length) % items.length
  selectedIndex.value = normalizedIndex
  items[normalizedIndex]?.focus()
}

const handleMenuKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Tab') {
    closeDropdown(false)
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    closeDropdown()
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    focusMenuItem(selectedIndex.value + (event.key === 'ArrowDown' ? 1 : -1))
    return
  }
  if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    focusMenuItem(event.key === 'Home' ? 0 : menuItems.value.length - 1)
  }
}

const handleButtonKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    toggleDropdown()
    return
  }

  if (event.key === 'ArrowDown' && !isDropdownOpen.value) {
    event.preventDefault()
    openDropdown()
  }
}

const toggleDropdown = () => {
  if (isMenuDisabled.value) return

  if (isDropdownOpen.value) {
    closeDropdown()
  } else {
    openDropdown()
  }
}

async function openDropdown() {
  isDropdownOpen.value = true
  selectedIndex.value = 0
  await nextTick()
  if (!isDropdownOpen.value) return
  updateDropdownPosition()
  focusMenuItem(0)
}

function closeDropdown(restoreButtonFocus = true) {
  isDropdownOpen.value = false
  selectedIndex.value = -1
  if (restoreButtonFocus) void nextTick(() => buttonRef.value?.focus())
}

const handleClickOutside = (event: Event) => {
  if (!isDropdownOpen.value) {
    return
  }

  if (buttonRef.value?.contains(event.target as Node)) {
    return
  }

  if (dropdownRef.value?.contains(event.target as Node)) {
    return
  }

  closeDropdown()
}

async function submitUserReport(reason: string) {
  try {
    const { data } = await reportApi.reportUser(props.userId, reason, '', { skipGlobalErrorHandler: true })
    if (!data.success) return false

    toastStore.addToast(t('report.reportSuccess'), 'success')
    return true
  } catch (error) {
    logger.error('Failed to report user:', error)
    toastStore.addToast(t('report.reportFailed'), 'error')
    return false
  }
}

useEventListener(() => document, 'click', handleClickOutside)
</script>

<style scoped>
.nv-user-menu-root {
  min-width: 0;
}

.nv-user-menu-button {
  width: 100%;
}

.nv-user-menu-button:hover:not(:disabled) {
  color: var(--nv-ink);
}
</style>
