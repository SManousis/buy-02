import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cart } from './cart';

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export type PaymentMethod = 'CASH_ON_DELIVERY';
export type PaymentStatus = 'UNPAID' | 'PAID';

export interface OrderItem {
  productId: string;
  name: string;
  unitPrice: number;
  quantity: number;
  imageId: string | null;
}

export interface ShippingAddress {
  line1: string;
  city: string;
  postalCode: string;
  country: string;
}

export interface StatusHistoryEntry {
  status: OrderStatus;
  changedAt: string;
  changedBy: string;
}

export interface Order {
  id: string;
  checkoutGroupId: string;
  buyerId: string;
  sellerId: string;
  items: OrderItem[];
  subtotal: number;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  statusHistory: StatusHistoryEntry[];
  shippingAddress: ShippingAddress;
  createdAt: string;
  updatedAt: string;
}

export interface CheckoutRequest {
  shippingAddress: ShippingAddress;
  paymentMethod: PaymentMethod;
}

export interface CheckoutResponse {
  checkoutGroupId: string;
  orders: Order[];
}

export interface UnavailableItem {
  productId: string;
  name: string;
  reason: string;
}

export interface ReorderResponse {
  cart: Cart;
  unavailableItems: UnavailableItem[];
}

/** One product's totals across a user's non-cancelled orders, named from the latest order snapshot. */
export interface ProductStat {
  productId: string;
  name: string;
  imageId: string | null;
  quantity: number;
  amount: number;
}

export interface BuyerStats {
  topProducts: ProductStat[];
  mostBoughtProducts: ProductStat[];
  totalSpent: number;
  orderCount: number;
}

export interface SellerStats {
  bestSellingProducts: ProductStat[];
  totalRevenue: number;
  orderCount: number;
}

/**
 * Mirrors the backend's forward-only, one-step-at-a-time transition chain
 * (see OrderStatusService#NEXT_STATUS). Used to drive seller status controls;
 * the backend remains the source of truth and rejects any other transition.
 */
const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  PENDING: 'CONFIRMED',
  CONFIRMED: 'SHIPPED',
  SHIPPED: 'DELIVERED',
};

export function nextOrderStatus(status: OrderStatus): OrderStatus | null {
  return NEXT_STATUS[status] ?? null;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private base = `${environment.apiBaseUrl}/orders`;

  constructor(private http: HttpClient) {}

  checkout(request: CheckoutRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.base}/checkout`, request);
  }

  mine(status?: OrderStatus): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.base}/mine`, { params: this.statusParams(status) });
  }

  selling(status?: OrderStatus): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.base}/selling`, { params: this.statusParams(status) });
  }

  getById(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.base}/${id}`);
  }

  updateStatus(id: string, status: OrderStatus): Observable<Order> {
    return this.http.patch<Order>(`${this.base}/${id}/status`, { status });
  }

  cancel(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/${id}/cancel`, {});
  }

  reorder(id: string): Observable<ReorderResponse> {
    return this.http.post<ReorderResponse>(`${this.base}/${id}/reorder`, {});
  }

  statsMine(): Observable<BuyerStats> {
    return this.http.get<BuyerStats>(`${this.base}/stats/me`);
  }

  statsSelling(): Observable<SellerStats> {
    return this.http.get<SellerStats>(`${this.base}/stats/selling`);
  }

  private statusParams(status?: OrderStatus): HttpParams {
    return status ? new HttpParams().set('status', status) : new HttpParams();
  }
}
