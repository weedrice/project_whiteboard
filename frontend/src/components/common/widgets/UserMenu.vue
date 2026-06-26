<template>
  <div class="nv-user-menu-root relative inline-block max-w-full text-left">
    <div class="min-w-0 max-w-full">
      <button
        :id="menuButtonId"
        ref="buttonRef"
        type="button"
        class="nv-user-menu-button inline-flex max-w-full min-w-0 items-center justify-start font-medium nv-text-muted focus:outline-none"
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
            :aria-selected="index === selectedIndex"
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
    <ReportModal :isOpen="isReportModalOpen" :userId="userId" :displayName="displayName" @close="closeReportModal" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, getCurrentInstance, nextTick, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEventListener } from '@/composables/useEventListener'
import { useKeyboardNavigation } from '@/composables/useKeyboardNavigation'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { useUserMenuActions } from '@/composables/useUserMenuActions'
import { useUserMenuPosition } from '@/composables/useUserMenuPosition'
import MessageModal from '@/components/user/MessageModal.vue'
import ReportModal from '@/components/report/ReportModal.vue'
import { formatUserDisplayName } from '@/utils/userDisplay'

const { t } = useI18n()

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
const menuInstanceId = `user-menu-${getCurrentInstance()?.uid ?? props.userId}`
const menuButtonId = `${menuInstanceId}-button`
const menuDropdownId = `${menuInstanceId}-dropdown`

const buttonLabel = computed(() => formatUserDisplayName(props.displayName, props.maxLabelLength, t('user.deletedUser')))

const buttonRef = ref<HTMLButtonElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)

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
  t
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

async function openDropdown() {
  isDropdownOpen.value = true
  resetMenuSelection()
  await nextTick()
  if (!isDropdownOpen.value) return
  updateDropdownPosition()
  trapFocus()
}

function closeDropdown() {
  isDropdownOpen.value = false
  restoreFocus()
  resetMenuSelection()
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
  color: var(--nv-text);
}
</style>
