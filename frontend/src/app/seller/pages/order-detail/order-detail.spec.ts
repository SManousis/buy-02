import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { SellerModule } from '../../seller-module';
import { Order, OrderService, OrderStatus } from '../../../shared/services/order';
import { SellerOrderDetail } from './order-detail';

function makeOrder(status: OrderStatus): Order {
  return {
    id: 'order-1234567',
    checkoutGroupId: 'group-1',
    buyerId: 'buyer-1234567',
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

describe('SellerOrderDetail', () => {
  let component: SellerOrderDetail;
  let fixture: ComponentFixture<SellerOrderDetail>;
  let getById: () => Observable<Order>;
  let updateStatus: () => Observable<Order>;
  let snackMessages: string[];

  beforeEach(async () => {
    getById = () => of(makeOrder('PENDING'));
    updateStatus = () => of(makeOrder('CONFIRMED'));
    snackMessages = [];

    await TestBed.configureTestingModule({
      imports: [SellerModule],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'order-1234567' } } } },
        {
          provide: OrderService,
          useValue: {
            getById: () => getById(),
            updateStatus: () => updateStatus(),
          },
        },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SellerOrderDetail);
    component = fixture.componentInstance;
  });

  it('shows a not-found state when the order request fails', () => {
    getById = () => throwError(() => new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Order not found');
  });

  it('renders order items, address, and buyer id after the request succeeds', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Olive oil');
    expect(fixture.nativeElement.textContent).toContain('1 Main St');
    expect(fixture.nativeElement.textContent).toContain('PENDING');
  });

  it('advances the order to the next status', () => {
    fixture.detectChanges();

    component.advance();

    expect(component.order?.status).toBe('CONFIRMED');
    expect(snackMessages).toEqual(['Order marked as CONFIRMED.']);
  });

  it('does not offer a status action for a delivered order', () => {
    getById = () => of(makeOrder('DELIVERED'));
    fixture.detectChanges();

    expect(component.nextStatus).toBeNull();
  });

  it('shows an invalid-transition message on a 400 response', () => {
    updateStatus = () => throwError(() => new HttpErrorResponse({
      status: 400,
      error: { message: 'Cannot transition order from PENDING to SHIPPED' },
    })) as unknown as Observable<Order>;
    fixture.detectChanges();

    component.advance();

    expect(snackMessages).toEqual(['Cannot transition order from PENDING to SHIPPED']);
  });
});
