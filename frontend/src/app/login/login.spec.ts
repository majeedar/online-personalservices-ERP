import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Login);
    el = fixture.nativeElement;
    // Initial session probe: not logged in.
    http.expectOne('/api/v1/auth/session').flush(
      { code: 'NOT_AUTHENTICATED', message: 'Please log in.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function submitButton(): HTMLButtonElement {
    return el.querySelector('button[type=submit]') as HTMLButtonElement;
  }

  it('validates required fields without calling the server', async () => {
    submitButton().click();
    await fixture.whenStable();

    expect(el.textContent).toContain('Username is required.');
    expect(el.textContent).toContain('Password is required.');
    http.expectNone('/api/v1/auth/login');
  });

  it('fills credentials from a demo account', async () => {
    const supervisor = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Supervisor'),
    ) as HTMLButtonElement;
    supervisor.click();
    await fixture.whenStable();

    const inputs = el.querySelectorAll('input');
    expect((inputs[0] as HTMLInputElement).value).toBe('supervisor');
    expect((inputs[1] as HTMLInputElement).value).toBe('demo123');
  });

  it('shows the server error message with its correlation ID', async () => {
    const [user, pass] = Array.from(el.querySelectorAll('input')) as HTMLInputElement[];
    user.value = 'employee';
    user.dispatchEvent(new Event('input'));
    pass.value = 'wrong';
    pass.dispatchEvent(new Event('input'));
    submitButton().click();

    http.expectOne('/api/v1/auth/login').flush(
      { code: 'INVALID_CREDENTIALS', message: 'Invalid username or password.', correlationId: 'abc-1' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();

    const alert = el.querySelector('[role=alert]');
    expect(alert?.textContent).toContain('Invalid username or password. (Ref: abc-1)');
  });
});
