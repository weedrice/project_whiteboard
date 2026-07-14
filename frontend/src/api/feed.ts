import type { AxiosRequestConfig } from 'axios'
import api from './index'
import type { ApiResponse, PersonalFeedItem, PersonalFeedPage } from '@/types'
import { mapApiDataResponse } from '@/api/response'
import { normalizePostSummary, type PostSummaryWire } from '@/api/postContract'
import { normalizePageResponseItems, type PageResponseRaw } from '@/utils/pageResponse'

export type PersonalFeedItemWire = Omit<PersonalFeedItem, 'isRead' | 'post'> & {
  read?: boolean
  isRead?: boolean
  post: PostSummaryWire | null
}

export type PersonalFeedPageWire = PageResponseRaw<PersonalFeedItemWire>

const normalizePersonalFeedItem = (item: PersonalFeedItemWire): PersonalFeedItem => ({
  ...item,
  isRead: item.isRead ?? item.read ?? false,
  post: item.post ? normalizePostSummary(item.post) : null,
})

export const normalizePersonalFeedPage = (page: PersonalFeedPageWire): PersonalFeedPage =>
  normalizePageResponseItems(page, normalizePersonalFeedItem)

export const feedApi = {
  getMyFeeds: (page = 0, size = 20, config?: AxiosRequestConfig) =>
    api.get<ApiResponse<PersonalFeedPageWire>>('/users/me/feeds', {
      ...config,
      params: {
        page,
        size,
        ...config?.params,
      },
    }).then((response) => mapApiDataResponse(response, normalizePersonalFeedPage)),
}
