<template>
  <div class="nv-user-menu-root relative inline-block max-w-full text-left">
    <div class="min-w-0 max-w-full">
      <button
        :id="menuButtonId"
        ref="buttonRef"
        type="button"
        class="nv-user-menu-button inline-flex max-w-full min-w-0 items-center justify-start font-medium text-gray-700 dark:text-gray-200 hover:text-indigo-600 dark:hover:text-indigo-400 focus:outline-none"
        :class="[
          { 'cursor-default hover:text-gray-700 dark:hover:text-gray-200': isMenuDisabled },
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
        class="absolute z-50 w-56 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 transition-colors duration-200 focus:outline-none dark:bg-gray-800 dark:ring-gray-700"
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
            :aria-selected="index === selectedIndex"
            :class="[
              'block w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-200',
              index === selectedIndex
                ? 'bg-indigo-50 dark:bg-indigo-900/20'
                : 'hover:bg-gray-100 dark:hover:bg-gray-700'
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
    <ReportModal :isOpen="isReportModalOpen" :userId="userId" :displayName="displayName" @close="closeReportModal" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, getCurrentInstance, nextTick, type CSSProperties } from 'vue'
import { userApi } from '@/api/user'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useKeyboardNavigation } from '@/composables/useKeyboardNavigation'
import { useFocusTrap } from '@/composables/useFocusTrap'
import MessageModal from '@/components/user/MessageModal.vue'
import ReportModal from '@/components/report/ReportModal.vue'
import { formatUserDisplayName } from '@/utils/userDisplay'
import { useQueryClient } from '@tanstack/vue-query'

const { t } = useI18n()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const queryClient = useQueryClient()

const props = withDefaults(defineProps<{
  userId: number
  displayName: string
  maxLabelLength?: number
  size?: 'default' | 'xs' | 'inherit'
}>(), {
  maxLabelLength: undefined,
  size: 'default'
})

const isDropdownOpen = ref(false)
const isMessageModalOpen = ref(false)
const isReportModalOpen = ref(false)
const menuInstanceId = `user-menu-${getCurrentInstance()?.uid ?? props.userId}`
const menuButtonId = `${menuInstanceId}-button`
const menuDropdownId = `${menuInstanceId}-dropdown`

const isSelf = computed(() => !!(authStore.user && authStore.user.userId === props.userId))
const isMenuDisabled = computed(() => !authStore.user || isSelf.value)
const buttonLabel = computed(() => formatUserDisplayName(props.displayName, props.maxLabelLength))

const buttonRef = ref<HTMLButtonElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)
const dropdownStyle = ref<CSSProperties>({})

const menuItems = computed(() => {
  if (isSelf.value) return []

  return [
    { action: openMessageModal, label: t('user.menu.sendMessage') },
    { action: openReportModal, label: t('user.menu.report') },
    { action: handleBlockUser, label: t('user.menu.block') }
  ]
})

const { selectedIndex, handleKeyDown: handleMenuKeyDown, setSelectedIndex, reset: resetMenuSelection } = useKeyboardNavigation(
  menuItems,
  {
    onSelect: (index) => {
      if (menuItems.value[index]) {
        menuItems.value[index].action()
      }
    },
    onEscape: () => {
      closeDropdown()
      buttonRef.value?.focus()
    },
    loop: true,
    initialIndex: -1
  }
)

const { trapFocus, restoreFocus } = useFocusTrap(dropdownRef, isDropdownOpen)

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

const updateDropdownPosition = () => {
  if (!buttonRef.value) {
    return
  }

  const rect = buttonRef.value.getBoundingClientRect()
  const horizontalPadding = 8
  const verticalPadding = 8
  const dropdownWidth = dropdownRef.value?.offsetWidth ?? 224
  const dropdownHeight = dropdownRef.value?.offsetHeight ?? 0
  const minLeft = window.scrollX + horizontalPadding
  const maxLeft = Math.max(minLeft, window.scrollX + window.innerWidth - dropdownWidth - horizontalPadding)
  const preferredTop = rect.bottom + window.scrollY + 5
  const preferredLeft = rect.left + window.scrollX
  let top = preferredTop

  if (dropdownHeight > 0) {
    const maxTop = window.scrollY + window.innerHeight - dropdownHeight - verticalPadding
    if (preferredTop > maxTop) {
      const aboveTop = rect.top + window.scrollY - dropdownHeight - 5
      top = aboveTop >= window.scrollY + verticalPadding
        ? aboveTop
        : Math.max(window.scrollY + verticalPadding, maxTop)
    }
  }

  dropdownStyle.value = {
    top: `${top}px`,
    left: `${Math.min(Math.max(preferredLeft, minLeft), maxLeft)}px`
  }
}

const openDropdown = async () => {
  isDropdownOpen.value = true
  resetMenuSelection()
  await nextTick()
  updateDropdownPosition()
  bindViewportListeners()
  trapFocus()
}

const closeDropdown = () => {
  isDropdownOpen.value = false
  unbindViewportListeners()
  restoreFocus()
  resetMenuSelection()
}

const openMessageModal = () => {
  closeDropdown()
  if (isSelf.value) return
  isMessageModalOpen.value = true
}

const closeMessageModal = () => {
  isMessageModalOpen.value = false
}

const openReportModal = () => {
  closeDropdown()
  if (isSelf.value) return
  isReportModalOpen.value = true
}

const closeReportModal = () => {
  isReportModalOpen.value = false
}

const handleBlockUser = async () => {
  closeDropdown()
  if (isSelf.value) return

  const isConfirmed = await confirm(t('user.block.confirm', { name: props.displayName }))
  if (!isConfirmed) {
    return
  }

  try {
    const { data } = await userApi.blockUser(props.userId)
    if (data.success) {
      toastStore.addToast(t('user.block.success', { name: props.displayName }), 'success')
      queryClient.invalidateQueries({ queryKey: ['comments'] })
    }
  } catch (error) {
    logger.error('Failed to block user:', error)
    toastStore.addToast(t('user.block.failed'), 'error')
  }
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

const handleViewportChange = () => {
  if (!isDropdownOpen.value) {
    return
  }

  updateDropdownPosition()
}

const bindViewportListeners = () => {
  window.addEventListener('resize', handleViewportChange)
  window.addEventListener('scroll', handleViewportChange, true)
}

const unbindViewportListeners = () => {
  window.removeEventListener('resize', handleViewportChange)
  window.removeEventListener('scroll', handleViewportChange, true)
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  unbindViewportListeners()
})
</script>

<style scoped>
.nv-user-menu-root {
  min-width: 0;
}

.nv-user-menu-button {
  width: 100%;
}
</style>
