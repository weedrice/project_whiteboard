<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { useAuthStore } from '@/stores/auth'
import { useHead } from '@unhead/vue'
import { ArrowLeft, ShoppingCart, Tag, Calendar, User, TrendingUp, Pencil, EyeOff, Eye } from 'lucide-vue-next'
import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const queryClient = useQueryClient()
const toastStore = useToastStore()

const emoticonId = computed(() => Number(route.params.emoticonId))

// 이모티콘 상세 조회
const { data: emoticon, isLoading, error } = useQuery({
  queryKey: ['emoticon', emoticonId],
  queryFn: async () => {
    const { data } = await emoticonApi.getEmoticon(emoticonId.value)
    return data.data
  },
  enabled: () => !!emoticonId.value
})

// 구매 상태 확인
const { data: purchaseStatus } = useQuery({
  queryKey: ['emoticon', emoticonId, 'purchased'],
  queryFn: async () => {
    const { data } = await emoticonApi.checkPurchaseStatus(emoticonId.value)
    return data.data
  },
  enabled: () => !!emoticonId.value && authStore.isAuthenticated
})

// 구매하기 mutation
const { mutate: purchase, isPending: isPurchasing } = useMutation({
  mutationFn: () => emoticonApi.purchaseEmoticon(emoticonId.value),
  onSuccess: () => {
    toastStore.addToast(t('emoticon.purchase.success'), 'success')
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId] })
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId, 'purchased'] })
  },
  onError: (error: any) => {
    const message = error.response?.data?.error?.message || t('emoticon.purchase.failed')
    toastStore.addToast(message, 'error')
  }
})

// 등록자 여부
const isOwner = computed(() => {
  if (!authStore.isAuthenticated) return false
  if (!emoticon.value) return false
  return emoticon.value.creatorId === authStore.user?.userId
})

// 구매 가능 여부 (숨김 처리된 노비콘은 구매 불가)
const canPurchase = computed(() => {
  if (!authStore.isAuthenticated) return false
  if (!emoticon.value) return false
  if (!emoticon.value.isActive) return false
  if (purchaseStatus.value?.purchased) return false
  if (emoticon.value.creatorId === authStore.user?.userId) return false
  return true
})

// 구매 버튼 텍스트
const purchaseButtonText = computed(() => {
  if (!authStore.isAuthenticated) return t('emoticon.purchase.button.loginRequired')
  if (purchaseStatus.value?.purchased) return t('emoticon.purchase.button.purchased')
  if (emoticon.value?.creatorId === authStore.user?.userId) return t('emoticon.purchase.button.myEmoticon')
  return t('emoticon.purchase.button.buyWithPrice', { price: purchaseStatus.value?.price || 100 })
})

// 목록으로 이동
const goToList = () => {
  router.push({ name: 'emoticon-list' })
}

// 수정 페이지로 이동
const goToEdit = () => {
  router.push({ name: 'emoticon-edit', params: { emoticonId: emoticonId.value } })
}

// 구매 처리
const handlePurchase = () => {
  if (!canPurchase.value) return
  if (confirm(t('emoticon.purchase.confirm'))) {
    purchase()
  }
}

// 숨김/표시 전환 mutation
const { mutate: toggleVisibility, isPending: isToggling } = useMutation({
  mutationFn: () => emoticonApi.toggleVisibility(emoticonId.value),
  onSuccess: (res) => {
    const isNowActive = res.data.data.isActive
    toastStore.addToast(
      isNowActive ? t('emoticon.visibility.showSuccess') : t('emoticon.visibility.hiddenSuccess'),
      'success'
    )
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId] })
    queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId, 'purchased'] })
    queryClient.invalidateQueries({ queryKey: ['emoticons'] })
  },
  onError: (err: any) => {
    const message = err.response?.data?.error?.message || t('emoticon.edit.failed')
    toastStore.addToast(message, 'error')
  }
})

const handleToggleVisibility = () => {
  if (!emoticon.value) return
  const verb = emoticon.value.isActive ? t('emoticon.visibility.hideConfirm') : t('emoticon.visibility.showConfirm')
  if (confirm(verb)) {
    toggleVisibility()
  }
}

// 날짜 포맷
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// 페이지 제목 설정
useHead({
  title: computed(() => emoticon.value?.name ? `${emoticon.value.name} - 노비콘` : '노비콘')
})
</script>

<template>
  <div class="max-w-5xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 뒤로가기 버튼 -->
    <div class="mb-6">
      <button
        @click="goToList"
        class="inline-flex items-center text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
      >
        <ArrowLeft class="w-4 h-4 mr-1" />
        목록으로
      </button>
    </div>

    <!-- 로딩 -->
    <div v-if="isLoading" class="animate-pulse">
      <div class="flex gap-6">
        <div class="w-40 h-40 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
        <div class="flex-1 space-y-4">
          <div class="h-8 bg-gray-200 dark:bg-gray-700 rounded w-1/3"></div>
          <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/4"></div>
          <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/2"></div>
        </div>
      </div>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-20">
      <p class="text-red-500 dark:text-red-400">이모티콘을 불러오는데 실패했습니다.</p>
      <BaseButton @click="goToList" variant="secondary" class="mt-4">
        목록으로 돌아가기
      </BaseButton>
    </div>

    <!-- 컨텐츠 -->
    <div v-else-if="emoticon">
      <!-- 상단 정보 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <div class="flex flex-col sm:flex-row gap-6">
          <!-- 썸네일 -->
          <div class="flex-shrink-0">
            <div class="w-40 h-40 bg-gray-100 dark:bg-gray-700 rounded-lg overflow-hidden">
              <img
                v-if="emoticon.thumbnailUrl"
                :src="emoticon.thumbnailUrl"
                :alt="emoticon.name"
                class="w-full h-full object-contain"
              />
              <div v-else class="w-full h-full flex items-center justify-center text-gray-400">
                No Image
              </div>
            </div>
          </div>

          <!-- 정보 -->
          <div class="flex-1">
            <div class="flex items-start justify-between mb-4 gap-2 flex-wrap">
              <div class="flex items-center gap-2 flex-wrap">
                <h1 class="text-2xl font-bold text-gray-900 dark:text-white">{{ emoticon.name }}</h1>
                <span
                  v-if="!emoticon.isActive"
                  class="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium bg-gray-200 text-gray-700 dark:bg-gray-600 dark:text-gray-300"
                >
                  {{ $t('emoticon.visibility.hidden') }}
                </span>
              </div>
              <div v-if="isOwner" class="flex items-center gap-2">
                <button
                  @click="handleToggleVisibility"
                  :disabled="isToggling"
                  :class="emoticon.isActive
                    ? 'inline-flex items-center px-3 py-1.5 text-sm bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300 rounded-lg hover:bg-amber-200 dark:hover:bg-amber-900/50 transition-colors'
                    : 'inline-flex items-center px-3 py-1.5 text-sm bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300 rounded-lg hover:bg-green-200 dark:hover:bg-green-900/50 transition-colors'"
                >
                  <EyeOff v-if="emoticon.isActive" class="w-4 h-4 mr-1" />
                  <Eye v-else class="w-4 h-4 mr-1" />
                  {{ emoticon.isActive ? $t('emoticon.visibility.hide') : $t('emoticon.visibility.show') }}
                </button>
                <button
                  @click="goToEdit"
                  class="inline-flex items-center px-3 py-1.5 text-sm bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300 rounded-lg hover:bg-indigo-200 dark:hover:bg-indigo-900/50 transition-colors"
                >
                  <Pencil class="w-4 h-4 mr-1" />
                  수정
                </button>
              </div>
            </div>
            
            <div class="space-y-2 text-sm">
              <div class="flex items-center text-gray-600 dark:text-gray-400">
                <User class="w-4 h-4 mr-2" />
                <span>등록자: {{ emoticon.creatorName }}</span>
              </div>
              <div class="flex items-center text-gray-600 dark:text-gray-400">
                <Calendar class="w-4 h-4 mr-2" />
                <span>등록일: {{ formatDate(emoticon.createdAt) }}</span>
              </div>
              <div class="flex items-center text-indigo-600 dark:text-indigo-400">
                <TrendingUp class="w-4 h-4 mr-2" />
                <span>판매 수량: {{ (emoticon.purchaseCount || 0).toLocaleString() }}개</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 이모티콘 이미지 목록 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          이모티콘 목록 <span class="text-sm font-normal text-gray-500">({{ emoticon.images?.length || 0 }}개)</span>
        </h2>
        
        <div v-if="emoticon.images && emoticon.images.length > 0" class="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 gap-3">
          <div
            v-for="image in emoticon.images"
            :key="image.imageId"
            class="aspect-square bg-gray-100 dark:bg-gray-700 rounded-lg overflow-hidden"
            style="width: 100px; height: 100px;"
          >
            <img
              :src="image.imageUrl"
              :alt="`${emoticon.name} - ${image.sortOrder + 1}`"
              class="w-full h-full object-contain"
            />
          </div>
        </div>
        <div v-else class="text-center py-8 text-gray-500 dark:text-gray-400">
          등록된 이미지가 없습니다.
        </div>
      </div>

      <!-- 태그 -->
      <div v-if="emoticon.tags && emoticon.tags.length > 0" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-8">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center">
          <Tag class="w-4 h-4 mr-2" />
          태그
        </h2>
        <div class="flex flex-wrap gap-2">
          <span
            v-for="tag in emoticon.tags"
            :key="tag"
            class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300"
          >
            #{{ tag }}
          </span>
        </div>
      </div>

      <!-- 구매 버튼 -->
      <div class="flex justify-end">
        <BaseButton
          @click="handlePurchase"
          :disabled="!canPurchase || isPurchasing"
          :variant="canPurchase ? 'primary' : 'secondary'"
          size="lg"
        >
          <ShoppingCart class="w-4 h-4 mr-2" />
          {{ isPurchasing ? $t('emoticon.purchase.purchasing') : purchaseButtonText }}
        </BaseButton>
      </div>
    </div>
  </div>
</template>
