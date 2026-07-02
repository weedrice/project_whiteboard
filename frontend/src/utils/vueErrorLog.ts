import type { ComponentPublicInstance } from 'vue'

function serializeError(error: unknown) {
    if (error instanceof Error) {
        return {
            name: error.name,
            message: error.message,
            stack: error.stack,
        }
    }

    return error
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
        error: serializeError(error),
        info,
        component: resolveComponentName(instance),
    }
}
