import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { I18N_PIPES } from './core/i18n/pipes';

@Component({
  selector: 'app-root',
  imports: [I18N_PIPES, RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
