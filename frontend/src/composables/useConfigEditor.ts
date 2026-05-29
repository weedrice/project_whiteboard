import { computed, ref, watch, type Ref } from 'vue'
import type { GlobalConfig } from '@/types'

export interface ConfigDraft {
    key: string
    value: string
    description?: string
}

type ConfigDrafts = Record<string, ConfigDraft>

function toDraft(config: GlobalConfig): ConfigDraft {
    return {
        key: config.key,
        value: config.value,
        description: config.description,
    }
}

function isSameDraft(left: ConfigDraft, right: ConfigDraft) {
    return left.value === right.value && left.description === right.description
}

export function useConfigEditor(configsData: Ref<GlobalConfig[] | undefined>) {
    const drafts = ref<ConfigDrafts>({})
    const baselines = ref<ConfigDrafts>({})

    watch(configsData, (configs) => {
        const nextDrafts: ConfigDrafts = {}
        const nextBaselines: ConfigDrafts = {}

        for (const config of configs ?? []) {
            const nextBaseline = toDraft(config)
            const previousDraft = drafts.value[config.key]
            const previousBaseline = baselines.value[config.key]
            const hasUnsavedDraft = previousDraft && previousBaseline && !isSameDraft(previousDraft, previousBaseline)

            nextDrafts[config.key] = hasUnsavedDraft ? { ...previousDraft } : nextBaseline
            nextBaselines[config.key] = nextBaseline
        }

        drafts.value = nextDrafts
        baselines.value = nextBaselines
    }, { immediate: true })

    const configs = computed(() => (configsData.value ?? []).map((config) => drafts.value[config.key] ?? toDraft(config)))

    function updateDraft(key: string, patch: Partial<Pick<ConfigDraft, 'value' | 'description'>>) {
        const current = drafts.value[key]
        if (!current) return

        drafts.value = {
            ...drafts.value,
            [key]: {
                ...current,
                ...patch,
            },
        }
    }

    function getDraft(key: string) {
        return drafts.value[key]
    }

    return {
        configs,
        updateDraft,
        getDraft,
    }
}
