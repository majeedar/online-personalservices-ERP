import { HttpInterceptorFn } from '@angular/common/http';
import { acceptLanguage } from './i18n';

/** Sends the interface language with every API call; the server answers in it (ADR-020). */
export const languageInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.url.startsWith('/api/') ? req.clone({ setHeaders: { 'Accept-Language': acceptLanguage() } }) : req);
