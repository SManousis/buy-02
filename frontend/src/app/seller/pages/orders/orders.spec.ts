import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { SellerModule } from '../../seller-module';
import { Order, OrderService, OrderStatus } from '../../../shared/services/order';
import { SellerOrders } from './orders';

function makeOrder(id: string, status: OrderStatus, buyerId = 'buyer-1', itemName = 'Olive oil'): Order {
  return {
    id,
    checkoutGroupId: 'group-1',
    buyerId,
    sellerId: 'seller-1',
    items: [{ productId: 'p1', name: itemName, unitPrice: 10, quantity: 2, imageId: null }],
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

describe('SellerOrders', () => {
  let component: SellerOrders;
  let fixture: ComponentFixture<SellerOrders>;
  let selling: (status?: OrderStatus) => Observable<Order[]>;
  let updateStatus: (id: string, status: OrderStatus) => Observable<Order>;
  let receivedStatus: OrderStatus | undefined;
  let snackMessages: string[];

  beforeEach(async () => {
    selling = (status) => { receivedStatus = status; return of([makeOrder('order-1', 'PENDING')]); };
    updateStatus = (id, status) => of(makeOrder(id, status));
    snackMessages = [];

    await TestBed.configureTestingModule({
      imports: [SellerModule],
      providers: [
        provideRouter([]),
        {
          provide: OrderService,
          useValue: {
            selling: (status?: OrderStatus) => selling(status),
            updateStatus: (id: string, status: OrderStatus) => updateStatus(id, status),
          },
        },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SellerOrders);
    component = fixture.componentInstance;
  });

  it('renders the seller\'s orders after the request succeeds', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('PENDING');
    expect(fixture.nativeElement.textContent).toContain('Mark as CONFIRMED');
  });

  it('shows an empty state when there are no orders', () => {
    selling = () => of([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No incoming orders yet');
  });

  it('shows an error state and retries when the request fails', () => {
    let calls = 0;
    selling = () => ++calls === 1 ? throwError(() => new Error('offline')) : of([makeOrder('order-1', 'PENDING')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('could not be loaded');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(calls).toBe(2);
  });

  it('reloads with the selected status filter', () => {
    fixture.detectChanges();

    component.onStatusChange('SHIPPED');

    expect(receivedStatus).toBe('SHIPPED');
  });

  it('filters orders client-side by order id, buyer id, or item name', () => {
    selling = () => of([
      makeOrder('order-aaa', 'PENDING', 'buyer-aaa', 'Olive oil'),
      makeOrder('order-bbb', 'PENDING', 'buyer-bbb', 'Honey'),
    ]);
    fixture.detectChanges();

    component.searchText = 'honey';

    expect(component.filteredOrders.length).toBe(1);
    expect(component.filteredOrders[0].id).toBe('order-bbb');
  });

  it('does not offer a status button for a terminal order', () => {
    selling = () => of([makeOrder('order-1', 'DELIVERED')]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Mark as');
  });

  it('advances an order to the next status', () => {
    fixture.detectChanges();

    component.advance(makeOrder('order-1', 'PENDING'));

    expect(snackMessages).toEqual(['Order marked as CONFIRMED.']);
  });

  it('shows an invalid-transition message on a 400 response', () => {
    updateStatus = () => throwError(() => new HttpErrorResponse({
      status: 400,
      error: { message: 'Cannot transition order from DELIVERED to CONFIRMED' },
    }));
    fixture.detectChanges();

    component.advance(makeOrder('order-1', 'PENDING'));

    expect(snackMessages).toEqual(['Cannot transition order from DELIVERED to CONFIRMED']);
  });
});
