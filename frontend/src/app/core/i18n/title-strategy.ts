import { effect, inject, Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { language, tr } from './i18n';

/** Route titles are English keys; shown translated and updated when the language changes. */
@Injectable({ providedIn: 'root' })
export class I18nTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private page: string | undefined;

  constructor() {
    super();
    effect(() => {
      language();
      this.apply();
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.page = this.buildTitle(snapshot);
    this.apply();
  }

  private apply(): void {
    const app = 'Online Personalservices';
    this.title.setTitle(this.page ? `${tr(this.page)} · ${tr(app)}` : tr(app));
  }
}
