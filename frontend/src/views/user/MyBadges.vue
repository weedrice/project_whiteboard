<script setup lang="ts">
import { computed } from 'vue'
import { Award, LockKeyhole, Medal } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { useUser } from '@/features/user/useUser'
import { useToastStore } from '@/stores/toast'
import { formatDate } from '@/utils/date'
import { useBadgeTranslation } from '@/features/user/useBadgeTranslation'

const { t } = useI18n()
const { badgeName, badgeDescription } = useBadgeTranslation()
const toastStore = useToastStore()
const { useMyBadges, useUpdateRepresentativeBadge } = useUser()
const {
  data: badges,
  isLoading,
  isError,
  refetch,
} = useMyBadges()
const {
  mutateAsync: updateRepresentativeBadge,
  isPending: isUpdatingRepresentativeBadge,
} = useUpdateRepresentativeBadge()

const acquiredBadges = computed(() => (badges.value ?? []).filter((badge) => badge.acquired))
const lockedBadges = computed(() => (badges.value ?? []).filter((badge) => !badge.acquired))

async function handleRepresentativeBadge(badgeCode: string | null) {
  try {
    await updateRepresentativeBadge(badgeCode)
    toastStore.addToast(t('user.badges.representativeUpdated'), 'success')
  } catch {
    toastStore.addToast(t('user.badges.representativeUpdateFailed'), 'error')
  }
}
</script>

<template>
  <section class="rounded-[var(--nv-radius-lg)] border nv-border nv-surface shadow">
    <header class="border-b nv-border p-4 sm:p-6">
      <div class="flex items-start gap-3">
        <span class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full nv-accent-bg nv-accent-text">
          <Award class="h-6 w-6" aria-hidden="true" />
        </span>
        <div>
          <p class="nv-kicker">{{ $t('user.navigation.activity') }}</p>
          <h1 class="mt-1 text-2xl font-semibold nv-title">{{ $t('user.badges.title') }}</h1>
          <p class="mt-2 text-sm nv-text-subtle">{{ $t('user.badges.description') }}</p>
        </div>
      </div>
    </header>

    <div v-if="isLoading" class="flex justify-center py-20" role="status" aria-live="polite">
      <BaseSpinner />
      <span class="sr-only">{{ $t('common.loading') }}</span>
    </div>

    <ErrorState
      v-else-if="isError"
      class="p-6"
      title-tag="h2"
      :message="$t('common.messages.loadFailed')"
      show-retry
      @retry="refetch()"
    />

    <div v-else class="space-y-8 p-4 sm:p-6">
      <dl class="grid grid-cols-3 gap-2 sm:gap-3">
        <div class="rounded-[var(--nv-radius-md)] nv-surface-muted p-3 text-center">
          <dt class="text-xs nv-text-subtle">{{ $t('user.badges.total') }}</dt>
          <dd class="mt-1 text-xl font-semibold nv-title">{{ badges?.length ?? 0 }}</dd>
        </div>
        <div class="rounded-[var(--nv-radius-md)] nv-surface-muted p-3 text-center">
          <dt class="text-xs nv-text-subtle">{{ $t('user.badges.acquired') }}</dt>
          <dd class="mt-1 text-xl font-semibold nv-accent-text">{{ acquiredBadges.length }}</dd>
        </div>
        <div class="rounded-[var(--nv-radius-md)] nv-surface-muted p-3 text-center">
          <dt class="text-xs nv-text-subtle">{{ $t('user.badges.locked') }}</dt>
          <dd class="mt-1 text-xl font-semibold nv-title">{{ lockedBadges.length }}</dd>
        </div>
      </dl>

      <section aria-labelledby="acquired-badges-title">
        <div class="flex items-center gap-2">
          <Medal class="h-5 w-5 nv-accent-text" aria-hidden="true" />
          <h2 id="acquired-badges-title" class="text-lg font-semibold nv-title">
            {{ $t('user.badges.acquired') }}
          </h2>
        </div>
        <p v-if="acquiredBadges.length === 0" class="mt-4 rounded-[var(--nv-radius-md)] nv-surface-muted p-5 text-sm nv-text-subtle">
          {{ $t('user.badges.acquiredEmpty') }}
        </p>
        <ul v-else class="mt-4 grid gap-3 md:grid-cols-2" role="list">
          <li
            v-for="badge in acquiredBadges"
            :key="badge.badgeCode"
            class="rounded-[var(--nv-radius-md)] border p-4"
            :class="badge.representative
              ? 'border-[var(--nv-accent)] nv-accent-bg'
              : 'nv-border nv-surface-muted'"
          >
            <div class="flex items-start gap-3">
              <span class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full nv-surface">
                <Award class="h-5 w-5 nv-accent-text" aria-hidden="true" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="font-semibold nv-title">{{ badgeName(badge) }}</h3>
                  <span v-if="badge.tier" class="rounded-full border nv-border px-2 py-0.5 text-xs font-semibold uppercase nv-text-subtle">
                    {{ badge.tier }}
                  </span>
                  <span v-if="badge.representative" class="rounded-full bg-[var(--nv-accent)] px-2 py-0.5 text-xs font-semibold text-white">
                    {{ $t('user.badges.representative') }}
                  </span>
                </div>
                <p class="mt-2 text-sm nv-text-subtle">{{ badgeDescription(badge) }}</p>
                <p v-if="badge.acquiredAt" class="mt-2 text-xs nv-text-muted">
                  {{ $t('user.badges.acquiredAt', { date: formatDate(badge.acquiredAt) }) }}
                </p>
              </div>
            </div>
            <div class="mt-4 flex justify-end border-t nv-border pt-3">
              <BaseButton
                type="button"
                size="sm"
                variant="secondary"
                :disabled="isUpdatingRepresentativeBadge"
                :aria-pressed="badge.representative"
                @click="handleRepresentativeBadge(badge.representative ? null : badge.badgeCode)"
              >
                {{ badge.representative
                  ? $t('user.badges.unsetRepresentative')
                  : $t('user.badges.setRepresentative') }}
              </BaseButton>
            </div>
          </li>
        </ul>
      </section>

      <section aria-labelledby="locked-badges-title">
        <div class="flex items-center gap-2">
          <LockKeyhole class="h-5 w-5 nv-text-subtle" aria-hidden="true" />
          <h2 id="locked-badges-title" class="text-lg font-semibold nv-title">
            {{ $t('user.badges.locked') }}
          </h2>
        </div>
        <p v-if="lockedBadges.length === 0" class="mt-4 rounded-[var(--nv-radius-md)] nv-surface-muted p-5 text-sm nv-text-subtle">
          {{ $t('user.badges.lockedEmpty') }}
        </p>
        <ul v-else class="mt-4 grid gap-3 md:grid-cols-2" role="list">
          <li
            v-for="badge in lockedBadges"
            :key="badge.badgeCode"
            class="rounded-[var(--nv-radius-md)] border nv-border nv-surface-muted p-4 opacity-70"
          >
            <div class="flex items-start gap-3">
              <span class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full nv-surface">
                <LockKeyhole class="h-5 w-5 nv-text-subtle" aria-hidden="true" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="font-semibold nv-title">{{ badgeName(badge) }}</h3>
                  <span v-if="badge.tier" class="rounded-full border nv-border px-2 py-0.5 text-xs font-semibold uppercase nv-text-subtle">
                    {{ badge.tier }}
                  </span>
                </div>
                <p class="mt-2 text-sm nv-text-subtle">{{ badgeDescription(badge) }}</p>
              </div>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>
