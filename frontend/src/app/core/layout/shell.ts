import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, inject, OnDestroy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { NotificationBadge } from '../../notifications/notification-badge.service';
import { AuthService } from '../auth/auth.service';
import { groupBySection, visibleNavItems } from '../navigation';
import { humanize } from '../format';
import { LanguageSwitch } from '../i18n/language-switch';
import { I18N_PIPES } from '../i18n/pipes';

@Component({
  selector: 'ops-shell',
  imports: [
    LanguageSwitch,
    I18N_PIPES,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly badge = inject(NotificationBadge);

  protected readonly session = this.auth.session;
  protected readonly unread = this.badge.unread;

  constructor() {
    this.badge.start();
  }

  ngOnDestroy(): void {
    this.badge.stop();
  }
  protected readonly navGroups = computed(() => groupBySection(visibleNavItems(this.auth.roles())));
  protected readonly roleLabels = computed(() => this.auth.roles().map(humanize).join(', '));
  protected readonly handset = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.Handset)
      .pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  protected async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/login']);
  }
}
