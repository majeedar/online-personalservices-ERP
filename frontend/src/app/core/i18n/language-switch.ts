import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Language, language, LANGUAGES, setLanguage, tr } from './i18n';

/** Language menu (ADR-019); the choice is kept in this browser. */
@Component({
  selector: 'ops-language-switch',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button
      mat-button
      type="button"
      [matMenuTriggerFor]="menu"
      [attr.aria-label]="label()"
      class="lang"
    >
      <mat-icon aria-hidden="true">translate</mat-icon>
      {{ current().toUpperCase() }}
    </button>
    <mat-menu #menu="matMenu">
      @for (l of languages; track l.code) {
        <button
          mat-menu-item
          type="button"
          (click)="choose(l.code)"
          [attr.aria-current]="l.code === current() ? 'true' : null"
          [attr.lang]="l.code"
        >
          <mat-icon>{{ l.code === current() ? 'check' : '' }}</mat-icon>
          <span>{{ l.label }}</span>
        </button>
      }
    </mat-menu>
  `,
  styles: `
    .lang {
      color: inherit;
      min-width: 0;
    }
  `,
})
export class LanguageSwitch {
  protected readonly languages = LANGUAGES;
  protected readonly current = language;

  protected label(): string {
    return tr('Language: {language}', {
      language: LANGUAGES.find((l) => l.code === language())?.label,
    });
  }

  protected choose(code: Language): void {
    setLanguage(code);
  }
}
