import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { CartModule } from '../../cart-module';
import { Cart, CartService } from '../../../shared/services/cart';
import { CartPage } from './cart-page';

const CART_WITH_ITEMS: Cart = {
  id: 'cart-1',
  items: [
    { productId: 'p1', sellerId: 's1', productName: 'Olive oil', quantity: 2, unitPriceSnapshot: 10, addedAt: '2024-01-01T00:00:00Z' },
  ],
  subtotal: 20,
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('CartPage', () => {
  let component: CartPage;
  let fixture: ComponentFixture<CartPage>;
  let get: () => Observable<Cart>;
  let updateItem: (productId: string, quantity: number) => Observable<Cart>;
  let removeItem: (productId: string) => Observable<Cart>;
  let clear: () => Observable<void>;
  let snackMessages: string[];

  beforeEach(async () => {
    get = () => of(CART_WITH_ITEMS);
    updateItem = () => of(CART_WITH_ITEMS);
    removeItem = () => of({ ...CART_WITH_ITEMS, items: [], subtotal: 0 });
    clear = () => of(undefined);
    snackMessages = [];

    await TestBed.configureTestingModule({
      imports: [CartModule],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            get: () => get(),
            updateItem: (id: string, qty: number) => updateItem(id, qty),
            removeItem: (id: string) => removeItem(id),
            clear: () => clear(),
          },
        },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CartPage);
    component = fixture.componentInstance;
  });

  it('shows a loading state before the cart request resolves', () => {
    const response = new Subject<Cart>();
    get = () => response;

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
  });

  it('renders cart items and the subtotal after the cart request succeeds', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Olive oil');
    expect(fixture.nativeElement.textContent).toContain('20');
  });

  it('shows an empty-cart state and hides the checkout link when the cart has no items', () => {
    get = () => of({ ...CART_WITH_ITEMS, items: [], subtotal: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Your cart is empty');
    expect(fixture.nativeElement.textContent).not.toContain('Proceed to checkout');
  });

  it('shows an error state and retries when the cart request fails', () => {
    let calls = 0;
    get = () => ++calls === 1 ? throwError(() => new Error('offline')) : of(CART_WITH_ITEMS);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('could not be loaded');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(calls).toBe(2);
  });

  it('increments an item quantity', () => {
    let received: { id: string; qty: number } | null = null;
    updateItem = (id, qty) => { received = { id, qty }; return of({ ...CART_WITH_ITEMS }); };
    fixture.detectChanges();

    component.increment('p1', 2);

    expect(received).toEqual({ id: 'p1', qty: 3 });
  });

  it('removes the item instead of decrementing below one', () => {
    let removedId: string | null = null;
    removeItem = (id) => { removedId = id; return of({ ...CART_WITH_ITEMS, items: [], subtotal: 0 }); };
    fixture.detectChanges();

    component.decrement('p1', 1);

    expect(removedId).toBe('p1');
  });

  it('shows a stock-conflict message and reloads on a 409 response', () => {
    updateItem = () => throwError(() => new HttpErrorResponse({
      status: 409,
      error: { message: 'Insufficient stock for product: Olive oil' },
    }));
    let loads = 0;
    get = () => { loads++; return of(CART_WITH_ITEMS); };
    fixture.detectChanges();

    component.increment('p1', 2);

    expect(snackMessages).toEqual(['Insufficient stock for product: Olive oil']);
    expect(loads).toBe(2);
  });

  it('decrements an item quantity above one', () => {
    let received: { id: string; qty: number } | null = null;
    updateItem = (id, qty) => { received = { id, qty }; return of({ ...CART_WITH_ITEMS }); };
    fixture.detectChanges();

    component.decrement('p1', 3);

    expect(received).toEqual({ id: 'p1', qty: 2 });
  });

  it('ignores a quantity change while another update is in progress', () => {
    let calls = 0;
    updateItem = () => { calls++; return new Subject<Cart>(); };
    fixture.detectChanges();

    component.increment('p1', 2);
    component.increment('p1', 2);

    expect(calls).toBe(1);
  });

  it('confirms when an item is removed', () => {
    fixture.detectChanges();

    component.remove('p1');

    expect(component.cart?.items).toEqual([]);
    expect(snackMessages).toEqual(['Item removed from cart.']);
  });

  it('clears the cart and reloads it', () => {
    let loads = 0;
    get = () => { loads++; return of(CART_WITH_ITEMS); };
    fixture.detectChanges();

    component.clear();

    expect(loads).toBe(2);
    expect(component.updatingProductId).toBeNull();
    expect(snackMessages).toEqual(['Cart cleared.']);
  });

  it('does not clear the cart while an update is in progress', () => {
    let cleared = false;
    clear = () => { cleared = true; return of(undefined); };
    fixture.detectChanges();
    component.updatingProductId = 'p1';

    component.clear();

    expect(cleared).toBe(false);
  });

  it('explains a missing item on a 404 response', () => {
    removeItem = () => throwError(() => new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();

    component.remove('p1');

    expect(snackMessages).toEqual(['That item is no longer in your cart. Refreshing…']);
  });

  it('explains a connection problem when the server cannot be reached', () => {
    clear = () => throwError(() => new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();

    component.clear();

    expect(snackMessages).toEqual(['Cannot reach the server. Check your connection.']);
  });

  it('falls back to a generic message for other errors', () => {
    updateItem = () => throwError(() => new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();

    component.increment('p1', 2);

    expect(snackMessages).toEqual(['Could not update your cart. Try again.']);
  });
});
