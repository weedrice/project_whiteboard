/**
 * Use when the mutation caller renders a domain-specific error message.
 * This prevents QueryClient from showing a second, generic error toast.
 */
export const LOCAL_MUTATION_ERROR_META = Object.freeze({ errorMessage: false })
