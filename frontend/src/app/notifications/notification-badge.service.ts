import { inject, Injectable, OnDestroy, signal } from '@angular/core';
import { Api } from '../core/api/api.service';

/** Unread-notification count for the toolbar bell, refreshed every minute while logged in. */
@Injectable({ providedIn: 'root' })
export class NotificationBadge implements OnDestroy {
  private readonly api = inject(Api);
  private timer?: ReturnType<typeof setInterval>;

  readonly unread = signal(0);

  start(): void {
    this.refresh();
    this.timer ??= setInterval(() => this.refresh(), 60_000);
  }

  stop(): void {
    clearInterval(this.timer);
    this.timer = undefined;
    this.unread.set(0);
  }

  refresh(): void {
    this.api.unreadCount$().subscribe({ next: (r) => this.unread.set(r.unread), error: () => undefined });
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
