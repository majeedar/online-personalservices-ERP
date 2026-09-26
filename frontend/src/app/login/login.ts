import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { describeError } from '../core/api/api-error';
import { AuthService } from '../core/auth/auth.service';

/** Fictitious demo personas (AGENT.md §55). All use the password demo123. */
export const DEMO_ACCOUNTS = [
  { username: 'employee', label: 'Employee' },
  { username: 'parttime', label: 'Part-time employee' },
  { username: 'supervisor', label: 'Supervisor' },
  { username: 'finance', label: 'Financial approver' },
  { username: 'travel', label: 'Travel office' },
  { username: 'timeadmin', label: 'Time admin' },
  { username: 'hradmin', label: 'HR admin' },
  { username: 'erpadmin', label: 'ERP admin' },
  { username: 'auditor', label: 'Auditor' },
];

@Component({
  selector: 'ops-login',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly demoAccounts = DEMO_ACCOUNTS;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(64)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  constructor() {
    // Obtain the XSRF-TOKEN cookie before the first POST, and skip the form if already logged in.
    void this.auth.ensureLoaded().then((session) => {
      if (session) {
        void this.router.navigateByUrl(this.returnUrl());
      }
    });
  }

  protected useDemoAccount(username: string): void {
    this.form.setValue({ username, password: 'demo123' });
    this.error.set(null);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    try {
      const { username, password } = this.form.getRawValue();
      await this.auth.login(username, password);
      await this.router.navigateByUrl(this.returnUrl());
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.submitting.set(false);
    }
  }

  private returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    // Only allow in-app paths, never absolute URLs (open-redirect protection).
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/dashboard';
  }
}
