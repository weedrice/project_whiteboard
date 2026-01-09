import { useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/vue-query'
import type { QueryKey } from '@tanstack/vue-query'

/**
 * Optimistic Update를 위한 Mutation 옵션
 */
export interface OptimisticUpdateOptions<TData, TVariables> {
    /**
     * 업데이트할 쿼리 키
     */
    queryKey: QueryKey
    
    /**
     * Optimistic update 함수
     * @param variables mutation 변수
     * @param previousData 이전 쿼리 데이터
     * @returns 업데이트된 데이터
     */
    optimisticUpdate: (variables: TVariables, previousData: TData | undefined) => TData
    
    /**
     * 에러 발생 시 롤백할 이전 데이터
     */
    onError?: (error: unknown, variables: TVariables, previousData: TData | undefined) => void
    
    /**
     * 성공 시 무효화할 추가 쿼리 키들
     */
    invalidateQueries?: QueryKey[]
}

/**
 * Optimistic Update를 지원하는 Mutation을 생성하는 헬퍼 함수
 * 
 * @example
 * ```typescript
 * const { mutate } = useOptimisticMutation({
 *   mutationFn: async (postId: number) => await postApi.likePost(postId),
 *   queryKey: ['post', postId],
 *   optimisticUpdate: (postId, oldPost) => ({
 *     ...oldPost,
 *     liked: true,
 *     likeCount: (oldPost?.likeCount || 0) + 1
 *   }),
 *   invalidateQueries: [['posts']]
 * })
 * ```
 */
export function useOptimisticMutation<TData = unknown, TVariables = unknown, TError = unknown>(
    mutationFn: (variables: TVariables) => Promise<any>,
    options: OptimisticUpdateOptions<TData, TVariables> & Omit<UseMutationOptions<any, TError, TVariables>, 'mutationFn' | 'onMutate' | 'onError' | 'onSettled'>
) {
    const queryClient = useQueryClient()
    const { queryKey, optimisticUpdate, onError, invalidateQueries = [] } = options

    return useMutation({
        mutationFn,
        onMutate: async (variables: TVariables) => {
            // 쿼리 키 계산 (함수인 경우 호출)
            const resolvedQueryKey = typeof queryKey === 'function' ? queryKey(variables) : queryKey

            // 진행 중인 쿼리 취소
            await queryClient.cancelQueries({ queryKey: resolvedQueryKey })

            // 이전 데이터 스냅샷 저장
            const previousData = queryClient.getQueryData<TData>(resolvedQueryKey)

            // Optimistic update 적용
            queryClient.setQueryData<TData>(resolvedQueryKey, (old: TData | undefined) => {
                return optimisticUpdate(variables, old)
            })

            // 롤백을 위한 컨텍스트 반환
            return { previousData, queryKey: resolvedQueryKey }
        },
        onError: (error: TError, variables: TVariables, context) => {
            // 에러 발생 시 이전 데이터로 롤백
            if (context?.previousData !== undefined && context?.queryKey) {
                queryClient.setQueryData(context.queryKey, context.previousData)
            }

            // 커스텀 에러 핸들러 호출
            if (onError) {
                onError(error, variables, context?.previousData)
            }
        },
        onSettled: (data, error, variables, context) => {
            const resolvedQueryKey = context?.queryKey || (typeof queryKey === 'function' ? queryKey(variables) : queryKey)
            
            // 성공/실패 여부와 관계없이 쿼리 무효화
            queryClient.invalidateQueries({ queryKey: resolvedQueryKey })
            
            // 추가 쿼리들도 무효화
            const resolvedInvalidateQueries = typeof invalidateQueries === 'function' 
                ? invalidateQueries(variables) 
                : invalidateQueries
            
            resolvedInvalidateQueries.forEach(key => {
                queryClient.invalidateQueries({ queryKey: key })
            })
        },
        ...options
    })
}
