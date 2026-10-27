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
    const draftRevisions = new Map<string, number>()
    const pendingSaves = new Map<string, { revision: number }>()
    let resetRevision = 0

    watch(configsData, (configs) => {
        const nextDrafts: ConfigDrafts = {}
        const nextBaselines: ConfigDrafts = {}

        for (const config of configs ?? []) {
            const nextBaseline = toDraft(config)
            const previousDraft = drafts.value[config.key]
            const previousBaseline = baselines.value[config.key]
            const hasUnsavedDraft = previousDraft && previousBaseline && !isSameDraft(previousDraft, previousBaseline)
            const pendingSave = pendingSaves.get(config.key)
            const editedDuringSave = pendingSave && pendingSave.revision !== (draftRevisions.get(config.key) ?? 0)

            nextDrafts[config.key] = previousDraft && (hasUnsavedDraft || editedDuringSave) ? { ...previousDraft } : nextBaseline
            nextBaselines[config.key] = nextBaseline
        }

        drafts.value = nextDrafts
        baselines.value = nextBaselines
    }, { immediate: true })

    const configs = computed(() => (configsData.value ?? []).map((config) => drafts.value[config.key] ?? toDraft(config)))
    const hasUnsavedChanges = computed(() => Object.keys(drafts.value).some((key) => {
        const draft = drafts.value[key]
        const baseline = baselines.value[key]
        return !!draft && !!baseline && !isSameDraft(draft, baseline)
    }))

    function updateDraft(key: string, patch: Partial<Pick<ConfigDraft, 'value' | 'description'>>) {
        const current = drafts.value[key]
        if (!current) return

        draftRevisions.set(key, (draftRevisions.get(key) ?? 0) + 1)
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

    function captureSave(key: string) {
        const draft = getDraft(key)
        if (!draft || pendingSaves.has(key)) return null
        const submittedRevision = draftRevisions.get(key) ?? 0
        const submittedResetRevision = resetRevision
        const pendingSave = { revision: submittedRevision }
        pendingSaves.set(key, pendingSave)

        return {
            draft: { ...draft },
            acknowledge(savedConfig: GlobalConfig) {
                if (submittedResetRevision !== resetRevision || pendingSaves.get(key) !== pendingSave || savedConfig.key !== key || !drafts.value[key]) return
                const savedDraft = toDraft(savedConfig)
                // Establish the saved baseline before clearing the submitted draft's dirty state.
                baselines.value = { ...baselines.value, [key]: savedDraft }
                if ((draftRevisions.get(key) ?? 0) === submittedRevision) {
                    drafts.value = { ...drafts.value, [key]: savedDraft }
                }
            },
            release() {
                if (pendingSaves.get(key) === pendingSave) pendingSaves.delete(key)
            },
        }
    }

    function resetDrafts() {
        resetRevision += 1
        draftRevisions.clear()
        pendingSaves.clear()
        drafts.value = {}
        baselines.value = {}
    }

    return {
        resetDrafts,
        configs,
        hasUnsavedChanges,
        updateDraft,
        getDraft,
        captureSave,
    }
}
