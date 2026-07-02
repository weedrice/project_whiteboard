import type { ComponentPublicInstance } from 'vue'

export function serializeErrorForLog(error: unknown) {
    if (error instanceof Error) {
        return {
            name: error.name,
            message: error.message,
            stack: error.stack,
        }
    }

    return error
}

export function createErrorLogPayload(error: unknown) {
    return {
        error: serializeErrorForLog(error),
    }
}

function resolveComponentName(instance: ComponentPublicInstance | null) {
    const componentType = instance?.$?.type
    if (!componentType) return null

    return componentType.name || componentType.__name || null
}

export function createVueErrorLogPayload(
    error: unknown,
    instance: ComponentPublicInstance | null,
    info: string,
) {
    return {
        error: serializeErrorForLog(error),
        info,
        component: resolveComponentName(instance),
    }
}
