import { HttpErrorResponse } from '@angular/common/http';
import { tr } from '../i18n/i18n';
import { ApiError } from './models';

/** Extracts a user-facing message (plus correlation ID for support) from any HTTP error. */
export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body.message === 'string') {
      // Already in the user's language: the API follows Accept-Language (ADR-020).
      const message = body.message;
      return body.correlationId ? `${message} (${tr('Ref')}: ${body.correlationId})` : message;
    }
    if (error.status === 0) {
      return tr('The server cannot be reached. Please try again later.');
    }
  }
  return tr('An unexpected error occurred.');
}

export function errorCode(error: unknown): string | undefined {
  if (error instanceof HttpErrorResponse) {
    return (error.error as Partial<ApiError> | null)?.code;
  }
  return undefined;
}
