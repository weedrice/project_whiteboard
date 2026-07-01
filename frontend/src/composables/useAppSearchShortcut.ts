import { onMounted } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { getAppSearchInput } from '@/composables/appShellModel'
import { useGlobalShortcuts } from '@/composables/useGlobalShortcuts'

export function useAppSearchShortcut(route: RouteLocationNormalizedLoaded, t: (key: string) => string) {
    const { registerShortcut } = useGlobalShortcuts()

    onMounted(() => {
        registerShortcut({
            key: '/',
            handler: () => {
                const searchInput = getAppSearchInput(route.name)
                if (searchInput) {
                    searchInput.focus()
                    searchInput.select()
                }
            },
            description: t('layout.shortcuts.focusSearch')
        })
    })
}
