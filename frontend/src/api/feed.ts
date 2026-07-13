import type { AxiosRequestConfig } from 'axios'
import api from './index'
import type { ApiResponse, PersonalFeedPage } from '@/types'

export const feedApi = {
  getMyFeeds: (page = 0, size = 20, config?: AxiosRequestConfig) =>
    api.get<ApiResponse<PersonalFeedPage>>('/users/me/feeds', {
      ...config,
      params: {
        page,
        size,
        ...config?.params,
      },
    }),
}
