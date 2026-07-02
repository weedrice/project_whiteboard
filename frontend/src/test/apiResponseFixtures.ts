import type { AxiosResponse } from 'axios'
import type { ApiResponse, PageResponse } from '@/types'

type AsyncApiFunction = (...args: never[]) => Promise<unknown>
type AsyncApiReturn<TFunction extends AsyncApiFunction> = Awaited<ReturnType<TFunction>>

export const apiDataResponse = <
  TFunction extends AsyncApiFunction,
  TData = unknown,
>(data: TData): AsyncApiReturn<TFunction> => ({
  data: {
    data,
  },
}) as AsyncApiReturn<TFunction>

export const apiEnvelopeResponse = <
  TFunction extends AsyncApiFunction,
  TData = unknown,
>(success: boolean, data: TData): AsyncApiReturn<TFunction> => ({
  data: {
    success,
    data,
  },
}) as AsyncApiReturn<TFunction>

export const apiSuccessDataResponse = <
  TFunction extends AsyncApiFunction,
  TData = unknown,
>(data: TData): AsyncApiReturn<TFunction> =>
  apiEnvelopeResponse<TFunction, TData>(true, data)

export const apiSuccessResponse = <
  TFunction extends AsyncApiFunction,
>(): AsyncApiReturn<TFunction> => ({
  data: {
    success: true,
  },
}) as AsyncApiReturn<TFunction>

export const pageResponseFixture = <TItem>(
  content: TItem[],
  overrides: Partial<PageResponse<TItem>> = {},
): PageResponse<TItem> => {
  const number = overrides.number ?? 0
  const size = overrides.size ?? content.length
  const totalElements = overrides.totalElements ?? content.length
  const totalPages = overrides.totalPages ?? (content.length > 0 ? 1 : 0)

  return {
    content,
    number,
    size,
    totalElements,
    totalPages,
    first: overrides.first ?? (number === 0),
    last: overrides.last ?? (totalPages === 0 || number >= totalPages - 1),
    empty: overrides.empty ?? (content.length === 0),
    ...overrides,
  }
}

export const apiPageSuccessDataResponse = <
  TFunction extends AsyncApiFunction,
  TItem = unknown,
>(
  content: TItem[],
  overrides: Partial<PageResponse<TItem>> = {},
): AsyncApiReturn<TFunction> =>
  apiSuccessDataResponse<TFunction, PageResponse<TItem>>(pageResponseFixture(content, overrides))

export const axiosApiPageSuccess = <TItem>(
  content: TItem[],
  overrides: Partial<PageResponse<TItem>> = {},
): AxiosResponse<ApiResponse<PageResponse<TItem>>> => ({
  data: {
    success: true,
    data: pageResponseFixture(content, overrides),
  },
  status: 200,
  statusText: 'OK',
  headers: {},
  config: {
    headers: undefined,
  },
}) as unknown as AxiosResponse<ApiResponse<PageResponse<TItem>>>
