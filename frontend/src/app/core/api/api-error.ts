import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/** Extracts a user-facing message (plus correlation ID for support) from any HTTP error. */
export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body.message === 'string') {
      return body.correlationId ? `${body.message} (Ref: ${body.correlationId})` : body.message;
    }
    if (error.status === 0) {
      return 'The server cannot be reached. Please try again later.';
    }
  }
  return 'An unexpected error occurred.';
}

export function errorCode(error: unknown): string | undefined {
  if (error instanceof HttpErrorResponse) {
    return (error.error as Partial<ApiError> | null)?.code;
  }
  return undefined;
}
