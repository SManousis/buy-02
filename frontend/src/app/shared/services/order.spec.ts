import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { Order, OrderService } from './order';

const ORDER: Order = {
  id: 'order-1',
  checkoutGroupId: 'group-1',
  buyerId: 'buyer-1',
  sellerId: 'seller-1',
  items: [{ productId: 'p1', name: 'Widget', unitPrice: 10, quantity: 2, imageId: null }],
  subtotal: 20,
  status: 'PENDING',
  paymentMethod: 'CASH_ON_DELIVERY',
  paymentStatus: 'UNPAID',
  statusHistory: [],
  shippingAddress: { line1: '1 Main St', city: 'Athens', postalCode: '10001', country: 'Greece' },
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('OrderService', () => {
  let service: OrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('submits a checkout request', () => {
    service.checkout({
      shippingAddress: { line1: '1 Main St', city: 'Athens', postalCode: '10001', country: 'Greece' },
      paymentMethod: 'CASH_ON_DELIVERY',
    }).subscribe();

    const request = http.expectOne('http://localhost:8080/orders/checkout');
    expect(request.request.method).toBe('POST');
    request.flush({ checkoutGroupId: 'group-1', orders: [ORDER] });
  });

  it('lists my orders without a status filter', () => {
    service.mine().subscribe();

    const request = http.expectOne('http://localhost:8080/orders/mine');
    expect(request.request.method).toBe('GET');
    request.flush([ORDER]);
  });

  it('lists my orders filtered by status', () => {
    service.mine('SHIPPED').subscribe();

    const request = http.expectOne('http://localhost:8080/orders/mine?status=SHIPPED');
    expect(request.request.method).toBe('GET');
    request.flush([ORDER]);
  });

  it('lists selling orders filtered by status', () => {
    service.selling('PENDING').subscribe();

    const request = http.expectOne('http://localhost:8080/orders/selling?status=PENDING');
    expect(request.request.method).toBe('GET');
    request.flush([ORDER]);
  });

  it('gets an order by id', () => {
    service.getById('order-1').subscribe();

    const request = http.expectOne('http://localhost:8080/orders/order-1');
    expect(request.request.method).toBe('GET');
    request.flush(ORDER);
  });

  it('updates an order status', () => {
    service.updateStatus('order-1', 'CONFIRMED').subscribe();

    const request = http.expectOne('http://localhost:8080/orders/order-1/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'CONFIRMED' });
    request.flush(ORDER);
  });

  it('cancels an order', () => {
    service.cancel('order-1').subscribe();

    const request = http.expectOne('http://localhost:8080/orders/order-1/cancel');
    expect(request.request.method).toBe('POST');
    request.flush(ORDER);
  });

  it('reorders an order', () => {
    service.reorder('order-1').subscribe();

    const request = http.expectOne('http://localhost:8080/orders/order-1/reorder');
    expect(request.request.method).toBe('POST');
    request.flush({ cart: { id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' }, unavailableItems: [] });
  });

  it('loads the buyer statistics', () => {
    let totalSpent: number | undefined;
    service.statsMine().subscribe((stats) => totalSpent = stats.totalSpent);

    const request = http.expectOne('http://localhost:8080/orders/stats/me');
    expect(request.request.method).toBe('GET');
    request.flush({ topProducts: [], mostBoughtProducts: [], totalSpent: 60, orderCount: 3 });

    expect(totalSpent).toBe(60);
  });

  it('loads the seller statistics', () => {
    let totalRevenue: number | undefined;
    service.statsSelling().subscribe((stats) => totalRevenue = stats.totalRevenue);

    const request = http.expectOne('http://localhost:8080/orders/stats/selling');
    expect(request.request.method).toBe('GET');
    request.flush({ bestSellingProducts: [], totalRevenue: 65, orderCount: 3 });

    expect(totalRevenue).toBe(65);
  });
});
