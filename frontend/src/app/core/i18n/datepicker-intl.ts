import { effect, Injectable } from '@angular/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';
import { language, tr } from './i18n';

/** Angular Material's own date-picker labels in the current language (ADR-019). */
@Injectable()
export class I18nDatepickerIntl extends MatDatepickerIntl {
  constructor() {
    super();
    effect(() => {
      language();
      this.calendarLabel = tr('Calendar');
      this.openCalendarLabel = tr('Open calendar');
      this.closeCalendarLabel = tr('Close calendar');
      this.prevMonthLabel = tr('Previous month');
      this.nextMonthLabel = tr('Next month');
      this.prevYearLabel = tr('Previous year');
      this.nextYearLabel = tr('Next year');
      this.prevMultiYearLabel = tr('Previous 24 years');
      this.nextMultiYearLabel = tr('Next 24 years');
      this.switchToMonthViewLabel = tr('Choose date');
      this.switchToMultiYearViewLabel = tr('Choose month and year');
      this.startDateLabel = tr('Start date');
      this.endDateLabel = tr('End date');
      this.changes.next();
    });
  }
}
