import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { OrdersModule } from '../../orders-module';
import { Order, OrderService, OrderStatus, ReorderResponse } from '../../../shared/services/order';
import { CartService } from '../../../shared/services/cart';
import { OrderDetail } from './order-detail';

function makeOrder(status: OrderStatus): Order {
  return {
    id: 'order-1234567',
    checkoutGroupId: 'group-1',
    buyerId: 'buyer-1',
    sellerId: 'seller-1',
    items: [{ productId: 'p1', name: 'Olive oil', unitPrice: 10, quantity: 2, imageId: null }],
    subtotal: 20,
    status,
    paymentMethod: 'CASH_ON_DELIVERY',
    paymentStatus: 'UNPAID',
    statusHistory: [{ status: 'PENDING', changedAt: '2024-01-01T00:00:00Z', changedBy: 'buyer-1' }],
    shippingAddress: { line1: '1 Main St', city: 'Athens', postalCode: '10001', country: 'Greece' },
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };
}

describe('OrderDetail', () => {
  let component: OrderDetail;
  let fixture: ComponentFixture<OrderDetail>;
  let getById: () => Observable<Order>;
  let cancel: () => Observable<Order>;
  let reorder: () => Observable<ReorderResponse>;
  let refreshCalled: boolean;
  let snackMessages: string[];
  let dialogResult: boolean;

  beforeEach(async () => {
    getById = () => of(makeOrder('PENDING'));
    cancel = () => of(makeOrder('CANCELLED'));
    reorder = () => of({ cart: { id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' }, unavailableItems: [] });
    refreshCalled = false;
    snackMessages = [];
    dialogResult = true;

    await TestBed.configureTestingModule({
      imports: [OrdersModule],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'order-1234567' } } } },
        {
          provide: OrderService,
          useValue: {
            getById: () => getById(),
            cancel: () => cancel(),
            reorder: () => reorder(),
          },
        },
        { provide: CartService, useValue: { refresh: () => { refreshCalled = true; } } },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogResult) }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderDetail);
    component = fixture.componentInstance;
  });

  it('shows a not-found state when the order request fails', () => {
    getById = () => throwError(() => new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Order not found');
  });

  it('renders order items, address, and status after the request succeeds', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Olive oil');
    expect(fixture.nativeElement.textContent).toContain('1 Main St');
    expect(fixture.nativeElement.textContent).toContain('PENDING');
  });

  it('allows cancelling a pending order and shows the updated status', () => {
    fixture.detectChanges();

    component.cancel();

    expect(component.order?.status).toBe('CANCELLED');
    expect(snackMessages).toEqual(['Order cancelled.']);
  });

  it('does not offer cancellation for a delivered order', () => {
    getById = () => of(makeOrder('DELIVERED'));
    fixture.detectChanges();

    expect(component.canCancel).toBe(false);
  });

  it('does not cancel when the confirm dialog is dismissed', () => {
    dialogResult = false;
    let cancelCalled = false;
    cancel = () => { cancelCalled = true; return of(makeOrder('CANCELLED')); };
    fixture.detectChanges();

    component.cancel();

    expect(cancelCalled).toBe(false);
  });

  it('reorders items and refreshes the cart badge', () => {
    fixture.detectChanges();

    component.reorder();

    expect(refreshCalled).toBe(true);
    expect(snackMessages).toEqual(['All items added to your cart!']);
  });

  it('reports unavailable items after a partial reorder', () => {
    reorder = () => of({
      cart: { id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' },
      unavailableItems: [{ productId: 'p1', name: 'Olive oil', reason: 'Out of stock' }],
    });
    fixture.detectChanges();

    component.reorder();

    expect(snackMessages).toEqual(['Some items could not be re-added: Olive oil']);
  });
});
