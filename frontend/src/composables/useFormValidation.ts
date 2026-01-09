import { reactive, ref, type Ref } from 'vue'

/**
 * 폼 필드 검증 규칙 타입
 */
export type ValidationRule<T = unknown> = (value: T) => string | true

/**
 * 폼 검증 규칙 맵
 */
export type ValidationRules<T extends Record<string, unknown>> = {
    [K in keyof T]?: ValidationRule<T[K]> | ValidationRule<T[K]>[]
}

/**
 * 실시간 폼 검증을 위한 composable
 * 
 * @param rules 검증 규칙 객체
 * @returns errors, touched, validate, validateAll, reset 함수
 * 
 * @example
 * ```typescript
 * const form = reactive({
 *   email: '',
 *   password: '',
 *   confirmPassword: ''
 * })
 * 
 * const { errors, touched, validate, validateAll, reset } = useFormValidation({
 *   email: (value) => {
 *     if (!value) return 'Email is required'
 *     if (!isValidEmail(value)) return 'Invalid email format'
 *     return true
 *   },
 *   password: (value) => {
 *     if (!value) return 'Password is required'
 *     if (!isValidPassword(value)) return 'Password must be at least 8 characters'
 *     return true
 *   },
 *   confirmPassword: (value) => {
 *     if (!value) return 'Please confirm password'
 *     if (value !== form.password) return 'Passwords do not match'
 *     return true
 *   }
 * })
 * 
 * // 실시간 검증
 * watch(() => form.email, (value) => {
 *   touched.email = true
 *   validate('email', value)
 * })
 * 
 * // 제출 시 전체 검증
 * const handleSubmit = () => {
 *   if (validateAll(form)) {
 *     // 제출 로직
 *   }
 * }
 * ```
 */
export function useFormValidation<T extends Record<string, unknown>>(
    rules: ValidationRules<T>
) {
    const errors = reactive<Partial<Record<keyof T, string>>>({})
    const touched = reactive<Partial<Record<keyof T, boolean>>>({})

    /**
     * 단일 필드 검증
     * @param field 필드명
     * @param value 필드 값
     * @returns 검증 성공 여부
     */
    const validate = (field: keyof T, value: T[keyof T]): boolean => {
        const rule = rules[field]
        if (!rule) {
            // 규칙이 없으면 에러 제거
            delete errors[field]
            return true
        }

        // 배열인 경우 모든 규칙 검증
        const ruleArray = Array.isArray(rule) ? rule : [rule]
        
        for (const r of ruleArray) {
            const result = r(value)
            if (result !== true) {
                errors[field] = result
                return false
            }
        }

        // 모든 규칙 통과
        delete errors[field]
        return true
    }

    /**
     * 전체 폼 검증
     * @param values 폼 값 객체
     * @returns 검증 성공 여부
     */
    const validateAll = (values: T): boolean => {
        let isValid = true
        
        // 모든 필드를 touched로 표시
        Object.keys(rules).forEach((field) => {
            touched[field as keyof T] = true
        })

        // 모든 필드 검증
        Object.keys(rules).forEach((field) => {
            const key = field as keyof T
            if (!validate(key, values[key])) {
                isValid = false
            }
        })

        return isValid
    }

    /**
     * 필드 touched 상태 설정
     * @param field 필드명
     * @param isTouched touched 상태
     */
    const setTouched = (field: keyof T, isTouched: boolean = true) => {
        touched[field] = isTouched
    }

    /**
     * 모든 필드 touched 상태 설정
     * @param isTouched touched 상태
     */
    const setAllTouched = (isTouched: boolean = true) => {
        Object.keys(rules).forEach((field) => {
            touched[field as keyof T] = isTouched
        })
    }

    /**
     * 검증 상태 리셋
     */
    const reset = () => {
        Object.keys(errors).forEach((field) => {
            delete errors[field as keyof T]
        })
        Object.keys(touched).forEach((field) => {
            delete touched[field as keyof T]
        })
    }

    /**
     * 특정 필드의 에러 제거
     * @param field 필드명
     */
    const clearError = (field: keyof T) => {
        delete errors[field]
    }

    /**
     * 모든 에러 제거
     */
    const clearAllErrors = () => {
        Object.keys(errors).forEach((field) => {
            delete errors[field as keyof T]
        })
    }

    /**
     * 필드에 에러가 있는지 확인
     * @param field 필드명
     * @returns 에러 존재 여부
     */
    const hasError = (field: keyof T): boolean => {
        return field in errors && errors[field] !== undefined
    }

    /**
     * 폼에 에러가 있는지 확인
     * @returns 에러 존재 여부
     */
    const hasAnyError = (): boolean => {
        return Object.keys(errors).length > 0
    }

    return {
        errors,
        touched,
        validate,
        validateAll,
        setTouched,
        setAllTouched,
        reset,
        clearError,
        clearAllErrors,
        hasError,
        hasAnyError
    }
}
