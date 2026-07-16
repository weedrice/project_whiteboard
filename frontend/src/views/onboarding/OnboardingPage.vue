<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Bell, Check, Compass } from 'lucide-vue-next'
import { boardApi } from '@/api/board'
import { unwrapAxiosApiData } from '@/api/response'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { useUser } from '@/composables/useUser'
import { invalidateBoardSubscriptionCaches } from '@/features/board/queries/boardCacheInvalidation'
import { usePushNotifications } from '@/features/notifications/usePushNotifications'
import type { BoardListItem } from '@/types'

const router = useRouter()
const route = useRoute()
const queryClient = useQueryClient()
const { t } = useI18n()
const { useCompleteOnboarding } = useUser()
const pushNotifications = usePushNotifications()
const pushMessage = ref('')
const pushIsError = ref(false)

const {
  data: boards,
  isLoading: isBoardsLoading,
  isError: isBoardsError,
  refetch: refetchBoards,
} = useQuery({
  queryKey: ['onboarding', 'board-recommendations'],
  queryFn: async () => unwrapAxiosApiData(await boardApi.getBoardRecommendations([])),
})

const { mutateAsync: completeOnboarding, isPending: isCompleting } = useCompleteOnboarding()
const subscribeMutation = useMutation({
  mutationFn: async (board: BoardListItem) => {
    await boardApi.subscribeBoard(board.boardUrl)
    return board
  },
  onSuccess: (board) => {
    queryClient.setQueryData<BoardListItem[]>(['onboarding', 'board-recommendations'], (current) =>
      current?.map((item) => item.boardUrl === board.boardUrl ? { ...item, isSubscribed: true } : item))
    invalidateBoardSubscriptionCaches(queryClient, board.boardUrl)
  },
})

const recommendedBoards = computed(() => boards.value ?? [])
const redirectTarget = computed(() => typeof route.query.redirect === 'string' ? route.query.redirect : '/')
const canEnablePush = computed(() => pushNotifications.supported.value && pushNotifications.enabled.value)

async function finishOnboarding() {
  await completeOnboarding()
  await router.replace(redirectTarget.value)
}

function subscribe(board: BoardListItem) {
  subscribeMutation.mutate(board)
}

async function enablePush() {
  try {
    await pushNotifications.enablePush()
    pushMessage.value = t('onboarding.pushEnabled')
    pushIsError.value = false
  } catch {
    pushMessage.value = t('onboarding.pushFailed')
    pushIsError.value = true
  }
}
</script>

<template>
  <div class="mx-auto max-w-5xl">
    <section class="space-y-2">
      <p class="nv-kicker">{{ $t('onboarding.kicker') }}</p>
      <h1 class="text-2xl font-semibold nv-title">{{ $t('onboarding.title') }}</h1>
      <p class="max-w-2xl nv-text-subtle">{{ $t('onboarding.description') }}</p>
    </section>

    <section class="mt-8 grid gap-6 lg:grid-cols-[1fr_320px]">
      <div class="space-y-4">
        <div class="flex items-center gap-2">
          <Compass class="h-5 w-5 nv-text-subtle" />
          <h2 class="text-lg font-semibold nv-title">{{ $t('onboarding.recommendedBoards') }}</h2>
        </div>

        <div v-if="isBoardsLoading" class="py-10">
          <BaseSpinner />
        </div>
        <ErrorState
          v-else-if="isBoardsError"
          :message="$t('common.messages.loadFailed')"
          show-retry
          @retry="refetchBoards"
        />
        <div v-else class="grid gap-3 sm:grid-cols-2">
          <article
            v-for="board in recommendedBoards"
            :key="board.boardId"
            class="rounded-[var(--nv-radius-md)] border nv-border p-4"
          >
            <div class="flex items-start justify-between gap-3">
              <div>
                <h3 class="font-semibold nv-title">{{ board.boardName }}</h3>
                <p class="mt-1 line-clamp-2 text-sm nv-text-subtle">{{ board.description }}</p>
              </div>
              <BaseButton
                size="sm"
                :disabled="board.isSubscribed || subscribeMutation.isPending.value"
                @click="subscribe(board)"
              >
                {{ board.isSubscribed ? $t('onboarding.subscribed') : $t('onboarding.subscribe') }}
              </BaseButton>
            </div>
          </article>
        </div>
      </div>

      <aside class="space-y-4 rounded-[var(--nv-radius-md)] border nv-border p-4">
        <div class="flex items-center gap-2">
          <Bell class="h-5 w-5 nv-text-subtle" />
          <h2 class="text-base font-semibold nv-title">{{ $t('onboarding.pushTitle') }}</h2>
        </div>
        <p class="text-sm nv-text-subtle">{{ $t('onboarding.pushDescription') }}</p>
        <BaseButton
          type="button"
          class="w-full"
          :disabled="!canEnablePush || pushNotifications.isEnabling.value"
          :loading="pushNotifications.isEnabling.value"
          @click="enablePush"
        >
          {{ $t('onboarding.enablePush') }}
        </BaseButton>
        <p
          v-if="pushMessage"
          class="text-sm"
          :role="pushIsError ? 'alert' : 'status'"
          :class="pushIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
        >
          {{ pushMessage }}
        </p>
      </aside>
    </section>

    <footer class="mt-8 flex justify-end gap-2">
      <BaseButton type="button" variant="secondary" :disabled="isCompleting" @click="finishOnboarding">
        {{ $t('onboarding.skip') }}
      </BaseButton>
      <BaseButton type="button" :loading="isCompleting" @click="finishOnboarding">
        <Check class="mr-1 h-4 w-4" />
        {{ $t('onboarding.finish') }}
      </BaseButton>
    </footer>
  </div>
</template>
