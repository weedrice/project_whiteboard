<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { Award, FileText, MessageSquare, User } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useUser } from '@/features/user/useUser'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import PostList from '@/components/board/PostList.vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { formatDate } from '@/utils/date'

const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const toastStore = useToastStore()
const {
  useUserProfile,
  useUserBadges,
  useUpdateRepresentativeBadge,
  usePublicProfilePosts,
  usePublicProfileComments,
} = useUser()

const activeTab = ref('overview')
const postPage = ref(0)
const commentPage = ref(0)
const userId = computed(() => String(route.params.userId || ''))
const postParams = computed(() => ({ page: postPage.value, size: 10, sort: 'createdAt,desc' }))
const commentParams = computed(() => ({ page: commentPage.value, size: 10, sort: 'createdAt,desc' }))
const tabs = computed(() => [
  { value: 'overview', label: t('user.publicProfile.overview') },
  { value: 'posts', label: t('user.publicProfile.posts') },
  { value: 'comments', label: t('user.publicProfile.comments') },
])

const { data: profile, isLoading: profileLoading, isError: profileError, refetch: refetchProfile } = useUserProfile(userId)
const { data: badges, isLoading: badgesLoading, isError: badgesError, refetch: refetchBadges } = useUserBadges(userId)
const { data: postsData, isLoading: postsLoading, isError: postsError, refetch: refetchPosts } = usePublicProfilePosts(userId, postParams)
const { data: commentsData, isLoading: commentsLoading, isError: commentsError, refetch: refetchComments } = usePublicProfileComments(userId, commentParams)
const { mutateAsync: updateRepresentativeBadge, isPending: isUpdatingRepresentativeBadge } = useUpdateRepresentativeBadge()
const acquiredBadges = computed(() => (badges.value || []).filter((badge) => badge.acquired))
const isOwnProfile = computed(() => String(authStore.user?.userId || '') === userId.value)

const seoTitle = computed(() => profile.value?.displayName || t('user.publicProfile.overview'))
const seoDescription = computed(() => {
  if (!profile.value) {
    return t('user.publicProfile.overviewDescription')
  }
  return `${profile.value.displayName} · ${t('user.publicProfile.postCount')} ${profile.value.postCount} · ${t('user.publicProfile.commentCount')} ${profile.value.commentCount}`
})

useHead({
  title: seoTitle,
  meta: [
    { name: 'robots', content: 'noindex, nofollow' },
    { name: 'googlebot', content: 'noindex, nofollow' },
    { name: 'description', content: seoDescription },
    { property: 'og:title', content: seoTitle },
    { property: 'og:description', content: seoDescription },
  ],
})

watch(userId, () => {
  activeTab.value = 'overview'
  postPage.value = 0
  commentPage.value = 0
})

async function handleRepresentativeBadge(badgeCode: string | null) {
  const targetUserId = userId.value
  const sessionGeneration = authStore.sessionGeneration
  const isCurrentAction = () => (
    userId.value === targetUserId
    && authStore.sessionGeneration === sessionGeneration
  )

  try {
    await updateRepresentativeBadge(badgeCode)
    if (!isCurrentAction()) return
    toastStore.addToast(t('user.publicProfile.representativeUpdated'), 'success')
  } catch {
    if (!isCurrentAction()) return
    toastStore.addToast(t('user.publicProfile.representativeUpdateFailed'), 'error')
  }
}
</script>

<template>
  <div class="mx-auto max-w-5xl">
    <div v-if="profileLoading" class="flex justify-center py-12" role="status" aria-live="polite">
      <BaseSpinner />
      <span class="sr-only">{{ t('common.loading') }}</span>
    </div>

    <ErrorState
      v-else-if="profileError"
      title-tag="h1"
      :message="t('common.messages.loadFailed')"
      show-retry
      @retry="refetchProfile()"
    />

    <div v-else-if="profile" class="space-y-6">
      <header class="rounded-md border border-[var(--nv-line)] bg-[var(--nv-surface)] p-5">
        <div class="flex flex-wrap items-center gap-4">
          <UserAvatar
            :image-url="profile.profileImageUrl"
            :name="profile.displayName"
            class="h-16 w-16"
          />
          <div class="min-w-0">
            <h1 class="truncate text-xl font-semibold nv-title">{{ profile.displayName }}</h1>
            <p class="mt-1 text-sm nv-text-subtle">
              {{ t('user.profile.joined') }}: {{ profile.createdAt ? formatDate(profile.createdAt) : '-' }}
            </p>
          </div>
          <div class="ml-auto flex-shrink-0">
            <UserMenu
              :user-id="profile.userId"
              :display-name="profile.displayName"
              :button-label="t('user.menu.actions')"
            />
          </div>
        </div>
        <div class="mt-5 grid grid-cols-2 gap-3 sm:max-w-sm">
          <div class="rounded-md bg-[var(--nv-surface-2)] p-3">
            <FileText class="mb-2 h-4 w-4 nv-text-subtle" />
            <p class="text-xs nv-text-subtle">{{ t('user.publicProfile.postCount') }}</p>
            <p class="text-lg font-semibold nv-title">{{ profile.postCount }}</p>
          </div>
          <div class="rounded-md bg-[var(--nv-surface-2)] p-3">
            <MessageSquare class="mb-2 h-4 w-4 nv-text-subtle" />
            <p class="text-xs nv-text-subtle">{{ t('user.publicProfile.commentCount') }}</p>
            <p class="text-lg font-semibold nv-title">{{ profile.commentCount }}</p>
          </div>
        </div>
      </header>

      <BaseSegmentedControl
        v-model="activeTab"
        :options="tabs"
        :label="t('user.publicProfile.tabs')"
        variant="joined"
      />

      <section v-if="activeTab === 'overview'" class="rounded-md border border-[var(--nv-line)] bg-[var(--nv-surface)] p-6">
        <User class="mb-3 h-5 w-5 nv-text-subtle" />
        <p class="text-sm nv-text-subtle">{{ t('user.publicProfile.overviewDescription') }}</p>
        <div class="mt-6 border-t border-[var(--nv-line)] pt-5">
          <div class="mb-3 flex items-center gap-2">
            <Award class="h-4 w-4 nv-text-subtle" />
            <h2 class="text-sm font-semibold nv-title">{{ t('user.publicProfile.badges') }}</h2>
          </div>
          <div v-if="badgesLoading" class="py-4">
            <BaseSpinner size="sm" />
          </div>
          <ErrorState
            v-else-if="badgesError"
            title-tag="h3"
            :message="t('common.messages.loadFailed')"
            show-retry
            @retry="refetchBadges()"
          />
          <div v-else-if="!acquiredBadges.length" class="text-sm nv-text-subtle">
            {{ t('user.publicProfile.badgesEmpty') }}
          </div>
          <div v-else class="grid gap-2 sm:grid-cols-2">
            <div
              v-for="badge in acquiredBadges"
              :key="badge.badgeCode"
              class="rounded-md border border-[var(--nv-line)] bg-[var(--nv-surface-2)] p-3"
            >
              <div class="flex items-start justify-between gap-2">
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold nv-title">{{ badge.name || badge.badgeCode }}</p>
                  <p class="mt-1 line-clamp-2 text-xs nv-text-subtle">{{ badge.description }}</p>
                </div>
                <span class="rounded-full border border-[var(--nv-line)] px-2 py-0.5 text-xs font-semibold uppercase nv-text-subtle">
                  {{ badge.tier }}
                </span>
              </div>
              <div v-if="isOwnProfile" class="mt-3">
                <BaseButton
                  type="button"
                  size="sm"
                  variant="secondary"
                  :disabled="isUpdatingRepresentativeBadge"
                  @click="handleRepresentativeBadge(badge.representative ? null : badge.badgeCode)"
                >
                  {{ badge.representative
                    ? t('user.publicProfile.unsetRepresentative')
                    : t('user.publicProfile.setRepresentative') }}
                </BaseButton>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-else-if="activeTab === 'posts'" class="rounded-md border border-[var(--nv-line)] bg-[var(--nv-surface)]">
        <ErrorState
          v-if="postsError"
          title-tag="h2"
          :message="t('common.messages.loadFailed')"
          show-retry
          @retry="refetchPosts()"
        />
        <PostList
          v-else
          :posts="postsData?.content || []"
          :loading="postsLoading"
          show-board-name
          hide-no-column
        />
        <div v-if="!postsError && postsData && postsData.totalPages > 1" class="border-t border-[var(--nv-line)] p-4">
          <Pagination :current-page="postPage" :total-pages="postsData.totalPages" @page-change="postPage = $event" />
        </div>
      </section>

      <section v-else class="rounded-md border border-[var(--nv-line)] bg-[var(--nv-surface)]">
        <div v-if="commentsLoading" class="flex justify-center py-10">
          <BaseSpinner />
        </div>
        <ErrorState
          v-else-if="commentsError"
          title-tag="h2"
          :message="t('common.messages.loadFailed')"
          show-retry
          @retry="refetchComments()"
        />
        <div v-else-if="!commentsData?.content.length" class="p-8 text-center text-sm nv-text-subtle">
          {{ t('user.publicProfile.emptyComments') }}
        </div>
        <ul v-else class="divide-y divide-[var(--nv-line)]">
          <li v-for="comment in commentsData.content" :key="comment.commentId" class="p-4">
            <router-link
              :to="`/board/${comment.post.boardUrl}/post/${comment.post.postId}#comment-${comment.commentId}`"
              class="font-medium nv-title hover:text-[var(--nv-accent)]"
            >
              {{ comment.post.title }}
            </router-link>
            <p class="mt-2 line-clamp-2 text-sm nv-text-subtle">{{ comment.content }}</p>
          </li>
        </ul>
        <div v-if="commentsData && commentsData.totalPages > 1" class="border-t border-[var(--nv-line)] p-4">
          <Pagination :current-page="commentPage" :total-pages="commentsData.totalPages" @page-change="commentPage = $event" />
        </div>
      </section>
    </div>
  </div>
</template>
