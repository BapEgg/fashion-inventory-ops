import type { ApiError } from '../api'

/**
 * A local error display for one work area (list/detail/simulation/decision/history), per the
 * React wiring spec section 9. Never shared as a single global error string -- each caller owns
 * its own `ApiError | null` state and renders its own `ProblemAlert`.
 */
export function ProblemAlert({ error, onRetry }: { error: ApiError; onRetry?: () => void }) {
  return (
    <div className="problem-alert" role="alert" aria-live="assertive">
      <p className="problem-alert__title">{error.title}</p>
      <p className="problem-alert__detail">{error.detail}</p>
      {error.fieldErrors && error.fieldErrors.length > 0 && (
        <ul className="problem-alert__field-errors">
          {error.fieldErrors.map((fieldError) => (
            <li key={`${fieldError.field}-${fieldError.code}`}>
              {fieldError.field}: {fieldError.message}
            </li>
          ))}
        </ul>
      )}
      <p className="problem-alert__meta">요청 ID: {error.requestId || '알 수 없음'}</p>
      {error.retryable && onRetry && (
        <button type="button" className="problem-alert__retry" onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  )
}
