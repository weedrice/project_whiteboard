import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type DropdownType = 'subscription' | 'all' | 'user' | 'notification' | null

export interface DropdownItem {
    label: string
    action: () => void
}

export const useKeyboardStore = defineStore('keyboard', () => {
    // 단축키 도움말 모달 상태
    const isShortcutsModalOpen = ref(false)

    // 현재 열린 드롭다운 타입
    const openDropdownType = ref<DropdownType>(null)

    // 현재 드롭다운에 표시된 항목들 (숫자키 선택용)
    const dropdownItems = ref<DropdownItem[]>([])

    // 드롭다운이 열려있는지 여부
    const isDropdownOpen = computed(() => openDropdownType.value !== null)

    // 단축키 모달 열기/닫기
    function toggleShortcutsModal() {
        isShortcutsModalOpen.value = !isShortcutsModalOpen.value
    }

    function openShortcutsModal() {
        isShortcutsModalOpen.value = true
    }

    function closeShortcutsModal() {
        isShortcutsModalOpen.value = false
    }

    // 드롭다운 상태 관리
    function setOpenDropdown(type: DropdownType, items: DropdownItem[] = []) {
        openDropdownType.value = type
        dropdownItems.value = items
    }

    function closeDropdown() {
        openDropdownType.value = null
        dropdownItems.value = []
    }

    // 숫자키로 항목 선택 (1-9: 인덱스 0-8, 0: 인덱스 9)
    function selectItemByNumberKey(key: string): boolean {
        if (!isDropdownOpen.value || dropdownItems.value.length === 0) {
            return false
        }

        let index = -1
        if (key >= '1' && key <= '9') {
            index = Number.parseInt(key, 10) - 1
        } else if (key === '0') {
            index = 9
        }

        if (index >= 0 && index < dropdownItems.value.length) {
            dropdownItems.value[index].action()
            closeDropdown()
            return true
        }

        return false
    }

    return {
        // State
        isShortcutsModalOpen,
        openDropdownType,
        dropdownItems,
        isDropdownOpen,

        // Actions
        toggleShortcutsModal,
        openShortcutsModal,
        closeShortcutsModal,
        setOpenDropdown,
        closeDropdown,
        selectItemByNumberKey,
    }
})
