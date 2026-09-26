import { HttpErrorResponse } from '@angular/common/http';
import { hasTranslation, tr } from '../i18n/i18n';
import { ApiError } from './models';

/**
 * A server message in the current language: the translation of `error.<CODE>` if
 * there is one (ADR-019), else the server's English text, which may carry details.
 */
export function serverMessage(code: string | null | undefined, message: string): string {
  const key = `error.${code}`;
  return code && hasTranslation(key) ? tr(key) : message;
}

/** Extracts a user-facing message (plus correlation ID for support) from any HTTP error. */
export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body.message === 'string') {
      const message = serverMessage(body.code, body.message);
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
