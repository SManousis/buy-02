import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { CartModule } from '../../cart-module';
import { Cart, CartService } from '../../../shared/services/cart';
import { CheckoutResponse, OrderService } from '../../../shared/services/order';
import { Checkout } from './checkout';

const CART_WITH_ITEMS: Cart = {
  id: 'cart-1',
  items: [
    { productId: 'p1', sellerId: 's1', productName: 'Olive oil', quantity: 2, unitPriceSnapshot: 10, addedAt: '2024-01-01T00:00:00Z' },
  ],
  subtotal: 20,
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('Checkout', () => {
  let component: Checkout;
  let fixture: ComponentFixture<Checkout>;
  let get: () => Observable<Cart>;
  let checkout: () => Observable<CheckoutResponse>;
  let resetCalled: boolean;
  let snackMessages: string[];

  beforeEach(async () => {
    get = () => of(CART_WITH_ITEMS);
    checkout = () => of({ checkoutGroupId: 'group-1', orders: [] } as unknown as CheckoutResponse);
    resetCalled = false;
    snackMessages = [];

    await TestBed.configureTestingModule({
      imports: [CartModule],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: { get: () => get(), reset: () => { resetCalled = true; } },
        },
        { provide: OrderService, useValue: { checkout: () => checkout() } },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Checkout);
    component = fixture.componentInstance;
  });

  it('does not submit an invalid form', () => {
    let submitted = false;
    checkout = () => { submitted = true; return of({ checkoutGroupId: 'g', orders: [] } as unknown as CheckoutResponse); };
    fixture.detectChanges();

    component.submit();

    expect(submitted).toBe(false);
  });

  it('submits the checkout request with the trimmed shipping address', () => {
    let received: unknown = null;
    checkout = () => { return of({ checkoutGroupId: 'g', orders: [{}] } as unknown as CheckoutResponse); };
    fixture.detectChanges();

    component.form.setValue({
      line1: '  1 Main St  ',
      city: ' Athens ',
      postalCode: ' 10001 ',
      country: ' Greece ',
      paymentMethod: 'CASH_ON_DELIVERY',
    });

    component.submit();

    expect(resetCalled).toBe(true);
    expect(snackMessages).toEqual(['Order placed successfully!']);
  });

  it('shows a conflict message and reloads the cart on a 409 response', () => {
    checkout = () => throwError(() => new HttpErrorResponse({
      status: 409,
      error: { message: 'Insufficient stock for product: Olive oil' },
    }));
    let loads = 0;
    get = () => { loads++; return of(CART_WITH_ITEMS); };
    fixture.detectChanges();

    component.form.setValue({
      line1: '1 Main St', city: 'Athens', postalCode: '10001', country: 'Greece', paymentMethod: 'CASH_ON_DELIVERY',
    });
    component.submit();

    expect(snackMessages).toEqual(['Insufficient stock for product: Olive oil']);
    expect(loads).toBe(2);
  });

  function submitValidForm(): void {
    component.form.setValue({
      line1: '1 Main St', city: 'Athens', postalCode: '10001', country: 'Greece', paymentMethod: 'CASH_ON_DELIVERY',
    });
    component.submit();
  }

  it('reports how many orders were placed when checkout splits by seller', () => {
    checkout = () => of({ checkoutGroupId: 'g', orders: [{}, {}] } as unknown as CheckoutResponse);
    fixture.detectChanges();

    submitValidForm();

    expect(snackMessages).toEqual(['2 orders placed successfully!']);
  });

  it.each([
    [400, 'Your cart is empty. Add items before checking out.'],
    [503, 'Product service is temporarily unavailable. Try again shortly.'],
    [0, 'Cannot reach the server. Check your connection.'],
    [500, 'Could not place your order. Try again.'],
  ])('shows the right message for a %i checkout response', (status, message) => {
    checkout = () => throwError(() => new HttpErrorResponse({ status }));
    fixture.detectChanges();

    submitValidForm();

    expect(snackMessages).toEqual([message]);
    expect(component.submitting).toBe(false);
  });

  it('shows an error state when the cart cannot be loaded', () => {
    get = () => throwError(() => new Error('offline'));
    fixture.detectChanges();

    expect(component.loadError).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Your cart could not be loaded');
  });

  it('shows an empty-cart state when the cart has no items', () => {
    get = () => of({ ...CART_WITH_ITEMS, items: [], subtotal: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Your cart is empty');
  });
});
