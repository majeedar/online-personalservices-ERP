import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Language, language, LANGUAGES, setLanguage, tr } from './i18n';

/**
 * Language menu (ADR-019, ADR-020). The choice is kept in this browser and saved to
 * the profile (used for e-mails); the page is reloaded so server texts switch too.
 */
@Component({
  selector: 'ops-language-switch',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button mat-button type="button" [matMenuTriggerFor]="menu" [attr.aria-label]="label()" class="lang">
      <mat-icon aria-hidden="true">translate</mat-icon>
      {{ current().toUpperCase() }}
    </button>
    <mat-menu #menu="matMenu">
      @for (l of languages; track l.code) {
        <button mat-menu-item type="button" (click)="choose(l.code)" [attr.aria-current]="l.code === current() ? 'true' : null" [attr.lang]="l.code">
          <mat-icon>{{ l.code === current() ? 'check' : '' }}</mat-icon>
          <span>{{ l.label }}</span>
        </button>
      }
    </mat-menu>
  `,
  styles: `.lang { color: inherit; min-width: 0; }`,
})
export class LanguageSwitch {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly languages = LANGUAGES;
  protected readonly current = language;

  protected label(): string {
    return tr('Language: {language}', { language: LANGUAGES.find((l) => l.code === language())?.label });
  }

  protected async choose(code: Language): Promise<void> {
    if (code === language()) {
      return;
    }
    setLanguage(code);
    if (!this.auth.isAuthenticated()) {
      return;
    }
    try {
      await firstValueFrom(this.http.put<void>('/api/v1/me/language', { language: code }));
    } catch {
      // The interface still switches; only the e-mail language stays as it was.
    }
    await this.reloadPage();
  }

  /** Loads the current page again, so texts from the server arrive in the new language. */
  private async reloadPage(): Promise<void> {
    const strategy = this.router.routeReuseStrategy;
    const reuse = strategy.shouldReuseRoute;
    strategy.shouldReuseRoute = () => false;
    try {
      await this.router.navigateByUrl(this.router.url, { onSameUrlNavigation: 'reload', replaceUrl: true });
    } finally {
      strategy.shouldReuseRoute = reuse;
    }
  }
}
