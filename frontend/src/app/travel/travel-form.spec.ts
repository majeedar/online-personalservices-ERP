import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TravelForm } from './travel-form';

describe('TravelForm', () => {
  let fixture: ComponentFixture<TravelForm>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TravelForm],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelForm);
    el = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/funding-sources').flush([
      { id: 'fs-1', costCentre: 'CC-2200', fundCode: 'BUDGET', description: 'Budget' },
      { id: 'fs-2', costCentre: 'CC-2201', projectCode: 'PRJ', fundCode: 'EU', description: 'Project' },
    ]);
    await fixture.whenStable();
  });

  function button(text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text)) as HTMLButtonElement;
  }

  it('requires purpose, destination, dates and cost centre before saving', async () => {
    button('Save draft').click();
    await fixture.whenStable();

    expect(el.textContent).toContain('Describe the purpose of the trip.');
    expect(el.textContent).toContain('Cost centre is required.');
    http.expectNone('/api/v1/travel');
  });

  it('shows the running percentage total of split funding', async () => {
    button('Add funding share').click();
    button('Add funding share').click();
    await fixture.whenStable();
    const component = fixture.componentInstance as unknown as { fundings: { at: (i: number) => { patchValue: (v: object) => void } } };
    component.fundings.at(0).patchValue({ fundingSourceId: 'fs-1', percentage: 50 });
    component.fundings.at(1).patchValue({ fundingSourceId: 'fs-2', percentage: 30 });
    await fixture.whenStable();

    expect(el.textContent).toContain('Percentage total: 80 %');
  });
});
