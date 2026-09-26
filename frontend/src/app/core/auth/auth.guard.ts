import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../api/models';
import { AuthService } from './auth.service';

/**
 * Requires a session and, if the route declares `data.roles`, one of those roles.
 * This is navigation convenience only; the API enforces authorization.
 */
export const authGuard: CanActivateFn = async (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const session = await auth.ensureLoaded();
  if (!session) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  const required = route.data?.['roles'] as Role[] | undefined;
  if (required && !required.some((r) => session.roles.includes(r))) {
    return router.createUrlTree(['/dashboard']);
  }
  return true;
};
