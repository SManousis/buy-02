import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { ProfileModule } from '../../profile-module';
import { BuyerStats, OrderService } from '../../../shared/services/order';
import { BuyerStatsPanel } from './buyer-stats';

const STATS: BuyerStats = {
  topProducts: [
    { productId: 'p1', name: 'Olive oil', imageId: null, quantity: 3, amount: 30 },
    { productId: 'p2', name: 'Honey', imageId: null, quantity: 1, amount: 25 },
  ],
  mostBoughtProducts: [
    { productId: 'p1', name: 'Olive oil', imageId: null, quantity: 3, amount: 30 },
    { productId: 'p2', name: 'Honey', imageId: null, quantity: 1, amount: 25 },
  ],
  totalSpent: 60,
  orderCount: 3,
};

describe('BuyerStatsPanel', () => {
  let fixture: ComponentFixture<BuyerStatsPanel>;
  let statsMine: () => Observable<BuyerStats>;

  beforeEach(async () => {
    statsMine = () => of(STATS);

    await TestBed.configureTestingModule({
      imports: [ProfileModule],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { statsMine: () => statsMine() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BuyerStatsPanel);
  });

  function text(): string {
    return fixture.nativeElement.textContent;
  }

  it('shows a loading state before the statistics arrive', () => {
    statsMine = () => new Subject<BuyerStats>();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
  });

  it('renders total spent, order count, and both ranked product lists', () => {
    fixture.detectChanges();

    expect(text()).toContain('€60.00');
    expect(text()).toContain('Top products');
    expect(text()).toContain('Most bought');
    const lists = fixture.nativeElement.querySelectorAll('app-product-stat-list');
    expect(lists.length).toBe(2);
    expect(lists[0].querySelector('.stat-row')?.textContent).toContain('€30.00');
    expect(lists[1].querySelector('.stat-row')?.textContent).toContain('3 bought');
  });

  it('links each product to its product page', () => {
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('.stat-name') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/products/p1');
  });

  it('shows an empty state for a buyer without purchases', () => {
    statsMine = () => of({ topProducts: [], mostBoughtProducts: [], totalSpent: 0, orderCount: 0 });
    fixture.detectChanges();

    expect(text()).toContain('No purchases yet');
    expect(text()).not.toContain('Total spent');
  });

  it('shows an error state and retries', () => {
    let calls = 0;
    statsMine = () => ++calls === 1 ? throwError(() => new Error('offline')) : of(STATS);
    fixture.detectChanges();

    expect(text()).toContain('Your purchase stats could not be loaded');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(calls).toBe(2);
    expect(text()).toContain('€60.00');
  });
});
