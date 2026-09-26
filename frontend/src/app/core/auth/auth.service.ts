import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Role, Session } from '../api/models';

/**
 * Holds the server session. Roles shown here only drive navigation; every
 * permission is enforced again by the backend (AGENT.md §46).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly current = signal<Session | null>(null);
  private loaded = false;

  readonly session = this.current.asReadonly();
  readonly isAuthenticated = computed(() => this.current() !== null);
  readonly roles = computed<Role[]>(() => this.current()?.roles ?? []);

  /** Resolves the session once per page load; also obtains the XSRF-TOKEN cookie. */
  async ensureLoaded(): Promise<Session | null> {
    if (!this.loaded) {
      try {
        this.current.set(await firstValueFrom(this.http.get<Session>('/api/v1/auth/session')));
      } catch {
        this.current.set(null);
      }
      this.loaded = true;
    }
    return this.current();
  }

  async login(username: string, password: string): Promise<Session> {
    const session = await firstValueFrom(
      this.http.post<Session>('/api/v1/auth/login', { username, password }),
    );
    this.current.set(session);
    this.loaded = true;
    return session;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/v1/auth/logout', {}));
    } finally {
      this.current.set(null);
    }
  }

  /** Called when the backend reports an expired session. */
  clear(): void {
    this.current.set(null);
  }

  hasAnyRole(...roles: Role[]): boolean {
    const mine = this.roles();
    return roles.some((r) => mine.includes(r));
  }
}
