import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { ProfileModule } from '../../profile-module';
import { OrderService, SellerStats } from '../../../shared/services/order';
import { SellerStatsPanel } from './seller-stats';

const STATS: SellerStats = {
  bestSellingProducts: [
    { productId: 'p1', name: 'Olive oil', imageId: null, quantity: 4, amount: 40 },
    { productId: 'p2', name: 'Honey', imageId: null, quantity: 1, amount: 25 },
  ],
  totalRevenue: 65,
  orderCount: 3,
};

describe('SellerStatsPanel', () => {
  let fixture: ComponentFixture<SellerStatsPanel>;
  let statsSelling: () => Observable<SellerStats>;

  beforeEach(async () => {
    statsSelling = () => of(STATS);

    await TestBed.configureTestingModule({
      imports: [ProfileModule],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { statsSelling: () => statsSelling() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SellerStatsPanel);
  });

  function text(): string {
    return fixture.nativeElement.textContent;
  }

  it('shows a loading state before the statistics arrive', () => {
    statsSelling = () => new Subject<SellerStats>();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
  });

  it('renders total revenue and best-selling products ranked by quantity', () => {
    fixture.detectChanges();

    expect(text()).toContain('€65.00');
    const rows = fixture.nativeElement.querySelectorAll('.stat-row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Olive oil');
    expect(rows[0].textContent).toContain('4 sold');
    expect(rows[0].textContent).toContain('€40.00');
  });

  it('shows an empty state for a seller without sales', () => {
    statsSelling = () => of({ bestSellingProducts: [], totalRevenue: 0, orderCount: 0 });
    fixture.detectChanges();

    expect(text()).toContain('No sales yet');
    expect(text()).not.toContain('Total revenue');
  });

  it('shows an error state and retries', () => {
    let calls = 0;
    statsSelling = () => ++calls === 1 ? throwError(() => new Error('offline')) : of(STATS);
    fixture.detectChanges();

    expect(text()).toContain('Your sales stats could not be loaded');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(calls).toBe(2);
    expect(text()).toContain('€65.00');
  });
});
