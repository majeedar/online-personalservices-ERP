import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { Api } from '../core/api/api.service';
import { describeError } from '../core/api/api-error';
import { AppNotification } from '../core/api/models';
import { NotificationBadge } from './notification-badge.service';
import { I18N_PIPES } from '../core/i18n/pipes';

@Component({
  selector: 'ops-notifications',
  imports: [I18N_PIPES, MatButtonModule, MatCardModule, MatIconModule],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ 'Notifications' | tr }}</h1>
        <p>{{ 'Also sent by e-mail when mail delivery is enabled.' | tr }}</p>
      </div>
      <button mat-stroked-button type="button" (click)="readAll()"><mat-icon>done_all</mat-icon> {{ 'Mark all read' | tr }}</button>
    </header>
    @if (error(); as e) {
      <p class="error-banner" role="alert">{{ e }}</p>
    }
    <mat-card appearance="outlined">
      <mat-card-content>
        <ul class="list">
          @for (n of items(); track n.id) {
            <li [class.unread]="!n.read">
              <mat-icon aria-hidden="true">{{ n.read ? 'notifications_none' : 'notifications_active' }}</mat-icon>
              <div class="body">
                <button type="button" class="link" (click)="open(n)">
                  <strong data-i18n-source="server">{{ n.subject }}</strong>
                  @if (!n.read) {
                    <span class="sr-only">{{ '(unread)' | tr }}</span>
                  }
                </button>
                <p data-i18n-source="server">{{ n.message }}</p>
                <small class="muted">{{ n.createdAt | ldate: 'medium' }}</small>
              </div>
            </li>
          } @empty {
            <li class="muted">{{ 'No notifications.' | tr }}</li>
          }
        </ul>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .list { list-style: none; margin: 0; padding: 0; }
    .list li { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .list li.unread strong { color: var(--mat-sys-primary); }
    .body p { margin: 4px 0; }
    .link { background: none; border: 0; padding: 0; cursor: pointer; text-align: left; font: inherit; color: inherit; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
})
export class Notifications {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly badge = inject(NotificationBadge);

  protected readonly items = signal<AppNotification[]>([]);
  protected readonly error = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  protected async open(n: AppNotification): Promise<void> {
    if (!n.read) {
      await this.api.markRead(n.id);
      this.badge.refresh();
    }
    const target =
      n.businessObjectType === 'AbsenceRequest'
        ? ['/absence', n.businessObjectId]
        : n.businessObjectType === 'TravelRequest'
          ? ['/travel', n.businessObjectId]
          : n.businessObjectType === 'TimeCorrectionRequest'
            ? ['/time/corrections', n.businessObjectId]
            : n.businessObjectType === 'UserTask'
              ? ['/tasks']
              : null;
    if (target) {
      await this.router.navigate(target as string[]);
    } else {
      await this.load();
    }
  }

  protected async readAll(): Promise<void> {
    await this.api.markAllRead();
    this.badge.refresh();
    await this.load();
  }

  private async load(): Promise<void> {
    try {
      this.items.set(await this.api.notifications(50));
    } catch (e) {
      this.error.set(describeError(e));
    }
  }
}
