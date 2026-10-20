import type { InjectionKey } from 'vue'
import type { createImageDownloadQueue } from '@/utils/imageDownloadQueue'

export const inquiryImageQueueKey: InjectionKey<ReturnType<typeof createImageDownloadQueue>> = Symbol('inquiryImageQueue')
