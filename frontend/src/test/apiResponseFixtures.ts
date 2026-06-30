type AsyncApiFunction = (...args: any[]) => Promise<unknown>
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
