import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { Cart, CartService } from './cart';

const EMPTY_CART: Cart = { id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' };

describe('CartService', () => {
  let service: CartService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CartService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the current cart through the API Gateway', () => {
    service.get().subscribe();

    const request = http.expectOne('http://localhost:8080/cart');
    expect(request.request.method).toBe('GET');
    request.flush(EMPTY_CART);
  });

  it('adds an item to the cart', () => {
    service.addItem('product-1', 2).subscribe();

    const request = http.expectOne('http://localhost:8080/cart/items');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ productId: 'product-1', quantity: 2 });
    request.flush(EMPTY_CART);
  });

  it('updates an item quantity', () => {
    service.updateItem('product-1', 5).subscribe();

    const request = http.expectOne('http://localhost:8080/cart/items/product-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ quantity: 5 });
    request.flush(EMPTY_CART);
  });

  it('removes an item from the cart', () => {
    service.removeItem('product-1').subscribe();

    const request = http.expectOne('http://localhost:8080/cart/items/product-1');
    expect(request.request.method).toBe('DELETE');
    request.flush(EMPTY_CART);
  });

  it('clears the cart', () => {
    service.clear().subscribe();

    const request = http.expectOne('http://localhost:8080/cart');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('tracks the total item count as an observable badge count', () => {
    const counts: number[] = [];
    service.itemCount$.subscribe((count) => counts.push(count));

    service.get().subscribe();
    http.expectOne('http://localhost:8080/cart').flush({
      id: 'cart-1',
      items: [
        { productId: 'p1', sellerId: 's1', productName: 'A', quantity: 2, unitPriceSnapshot: 10, addedAt: '2024-01-01T00:00:00Z' },
        { productId: 'p2', sellerId: 's1', productName: 'B', quantity: 3, unitPriceSnapshot: 5, addedAt: '2024-01-01T00:00:00Z' },
      ],
      subtotal: 35,
      updatedAt: '2024-01-01T00:00:00Z',
    });

    expect(counts).toEqual([0, 5]);
  });

  it('resets the badge count to zero', () => {
    service.addItem('product-1', 2).subscribe();
    http.expectOne('http://localhost:8080/cart/items').flush({
      id: 'cart-1',
      items: [{ productId: 'p1', sellerId: 's1', productName: 'A', quantity: 2, unitPriceSnapshot: 10, addedAt: '2024-01-01T00:00:00Z' }],
      subtotal: 20,
      updatedAt: '2024-01-01T00:00:00Z',
    });

    service.reset();

    let latest = -1;
    service.itemCount$.subscribe((count) => (latest = count));
    expect(latest).toBe(0);
  });
});
