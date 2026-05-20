<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { adApi, type Ad } from '@/api/ad'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  placement?: string
}>(), {
  placement: 'SIDEBAR'
})

const ad = ref<Ad | null>(null)
const loading = ref(true)
const impressionRecorded = ref(false)
let isMounted = false
let adRequestController: AbortController | null = null
let impressionAbortController: AbortController | null = null

function setFallbackAd() {
  ad.value = {
    adId: null,
    title: t('common.advertisement'),
    imageUrl: null,
    targetUrl: null
  }
}

function normalizeExternalUrl(rawUrl: string | null | undefined): string | null {
  if (!rawUrl) return null
  try {
    const parsed = new URL(rawUrl, window.location.origin)
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return null
    }
    return parsed.toString()
  } catch {
    return null
  }
}

function openExternalUrl(rawUrl: string | null | undefined) {
  const safeUrl = normalizeExternalUrl(rawUrl)
  if (!safeUrl) {
    logger.warn('Blocked unsafe ad target URL:', rawUrl)
    return
  }

  const popup = window.open(safeUrl, '_blank', 'noopener,noreferrer')
  if (popup) {
    popup.opener = null
  }
}

const fetchAd = async () => {
  adRequestController?.abort()
  const controller = new AbortController()
  adRequestController = controller

  try {
    const nextAd = await adApi.getAd(props.placement, { signal: controller.signal })
    if (!isMounted || controller.signal.aborted) {
      return
    }
    if (nextAd) {
      ad.value = nextAd
      void recordImpression()
    }
  } catch (error) {
    if (!isMounted || controller.signal.aborted) {
      return
    }
    logger.warn('Failed to load ad:', error)
    setFallbackAd()
  } finally {
    if (adRequestController === controller) {
      adRequestController = null
    }
    if (isMounted && !controller.signal.aborted) {
      loading.value = false
      if (!ad.value) {
        setFallbackAd()
      }
    }
  }
}

const recordImpression = async () => {
  if (!ad.value?.adId || impressionRecorded.value) {
    return
  }

  impressionAbortController?.abort()
  const controller = new AbortController()
  impressionAbortController = controller

  try {
    await adApi.recordImpression(ad.value.adId, { signal: controller.signal })
    if (!isMounted || controller.signal.aborted) {
      return
    }
    impressionRecorded.value = true
  } catch (error) {
    if (!isMounted || controller.signal.aborted) {
      return
    }
    logger.warn('Failed to record ad impression:', error)
  } finally {
    if (impressionAbortController === controller) {
      impressionAbortController = null
    }
  }
}

const handleAdClick = async () => {
  if (!ad.value) return

  try {
    if (ad.value.adId) {
      const targetUrl = await adApi.recordClick(ad.value.adId)
      if (targetUrl) {
        openExternalUrl(targetUrl)
      } else if (ad.value.targetUrl) {
        openExternalUrl(ad.value.targetUrl)
      }
    } else if (ad.value.targetUrl) {
      openExternalUrl(ad.value.targetUrl)
    }
  } catch (error) {
    logger.error('Ad click error:', error)
    if (ad.value.targetUrl) {
      openExternalUrl(ad.value.targetUrl)
    }
  }
}

onMounted(() => {
  isMounted = true
  fetchAd()
})

onUnmounted(() => {
  isMounted = false
  adRequestController?.abort()
  adRequestController = null
  impressionAbortController?.abort()
  impressionAbortController = null
})
</script>

<template>
  <div v-if="ad" class="my-4 text-center">
    <div class="cursor-pointer overflow-hidden rounded-lg shadow-md hover:shadow-lg transition-shadow duration-300"
      :class="placement === 'SIDEBAR' ? 'inline-block' : 'block w-full'" @click="handleAdClick">
      <img v-if="ad.imageUrl" :src="ad.imageUrl" :alt="ad.title" class="max-w-full h-auto object-cover"
        :class="placement === 'SIDEBAR' ? 'w-40 h-[600px]' : 'w-full h-auto'" />
      <div v-else class="bg-gray-100 p-4 flex items-center justify-center text-gray-400 text-sm"
        :class="placement === 'SIDEBAR' ? 'w-40 h-[600px]' : 'w-full h-[120px]'">
        <span>{{ ad.title || $t('common.advertisement') }}</span>
      </div>
    </div>
  </div>
</template>
