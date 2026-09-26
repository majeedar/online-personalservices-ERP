import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function run(roles?: string[]): Promise<boolean | UrlTree> {
    const route = { data: roles ? { roles } : {} } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/profile' } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(route, state)) as Promise<boolean | UrlTree>;
  }

  it('redirects to login with a return URL when there is no session', async () => {
    const result = run();
    http.expectOne('/api/v1/auth/session').flush(
      { code: 'NOT_AUTHENTICATED', message: 'Please log in.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    const tree = (await result) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(tree)).toBe('/login?returnUrl=%2Fprofile');
  });

  it('allows a logged-in user', async () => {
    const result = run();
    http
      .expectOne('/api/v1/auth/session')
      .flush({ employeeId: '1', username: 'employee', displayName: 'E', roles: ['EMPLOYEE'] });
    expect(await result).toBe(true);
  });

  it('sends users without a required role to the dashboard', async () => {
    const result = run(['ERP_ADMIN']);
    http
      .expectOne('/api/v1/auth/session')
      .flush({ employeeId: '1', username: 'employee', displayName: 'E', roles: ['EMPLOYEE'] });
    const tree = (await result) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(tree)).toBe('/dashboard');
  });
});
