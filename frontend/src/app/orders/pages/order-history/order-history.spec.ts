import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, of, throwError } from 'rxjs';

import { OrdersModule } from '../../orders-module';
import { Order, OrderService, OrderStatus } from '../../../shared/services/order';
import { OrderHistory } from './order-history';

function makeOrder(id: string, status: OrderStatus): Order {
  return {
    id,
    checkoutGroupId: 'group-1',
    buyerId: 'buyer-1',
    sellerId: 'seller-1',
    items: [{ productId: 'p1', name: 'Olive oil', unitPrice: 10, quantity: 2, imageId: null }],
    subtotal: 20,
    status,
    paymentMethod: 'CASH_ON_DELIVERY',
    paymentStatus: 'UNPAID',
    statusHistory: [],
    shippingAddress: { line1: '1 Main St', city: 'Athens', postalCode: '10001', country: 'Greece' },
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };
}

describe('OrderHistory', () => {
  let component: OrderHistory;
  let fixture: ComponentFixture<OrderHistory>;
  let mine: (status?: OrderStatus) => Observable<Order[]>;
  let receivedStatus: OrderStatus | undefined;
  let snackMessages: string[];

  beforeEach(async () => {
    mine = (status) => { receivedStatus = status; return of([makeOrder('order-1', 'PENDING')]); };
    snackMessages = [];

    await TestBed.configureTestingModule({
      imports: [OrdersModule],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: (status?: OrderStatus) => mine(status) } },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderHistory);
    component = fixture.componentInstance;
  });

  it('shows a loading state before orders resolve', () => {
    mine = () => new Observable<Order[]>();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
  });

  it('renders orders after the request succeeds', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('PENDING');
  });

  it('shows an empty state when there are no orders', () => {
    mine = () => of([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No orders');
  });

  it('shows an error state and retries when the request fails', () => {
    let calls = 0;
    mine = () => ++calls === 1 ? throwError(() => new Error('offline')) : of([makeOrder('order-1', 'PENDING')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('could not be loaded');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(calls).toBe(2);
  });

  it('reloads with the selected status filter', () => {
    fixture.detectChanges();

    component.onStatusChange('CONFIRMED');

    expect(receivedStatus).toBe('CONFIRMED');
  });

  it('reloads when a status is picked from the filter dropdown', () => {
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('#status-filter') as HTMLSelectElement;
    select.value = 'SHIPPED';
    select.dispatchEvent(new Event('change'));

    expect(receivedStatus).toBe('SHIPPED');
    expect(component.selectedStatus).toBe('SHIPPED');
  });
});
