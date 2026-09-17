import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CartItem {
  productId: string;
  sellerId: string;
  productName: string;
  quantity: number;
  unitPriceSnapshot: number;
  addedAt: string;
}

export interface Cart {
  id: string;
  items: CartItem[];
  subtotal: number;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private base = `${environment.apiBaseUrl}/cart`;

  private itemCountSubject = new BehaviorSubject<number>(0);
  readonly itemCount$ = this.itemCountSubject.asObservable();

  constructor(private http: HttpClient) {}

  get(): Observable<Cart> {
    return this.http.get<Cart>(this.base).pipe(tap((cart) => this.updateCount(cart)));
  }

  addItem(productId: string, quantity: number): Observable<Cart> {
    return this.http.post<Cart>(`${this.base}/items`, { productId, quantity })
      .pipe(tap((cart) => this.updateCount(cart)));
  }

  updateItem(productId: string, quantity: number): Observable<Cart> {
    return this.http.put<Cart>(`${this.base}/items/${productId}`, { quantity })
      .pipe(tap((cart) => this.updateCount(cart)));
  }

  removeItem(productId: string): Observable<Cart> {
    return this.http.delete<Cart>(`${this.base}/items/${productId}`)
      .pipe(tap((cart) => this.updateCount(cart)));
  }

  clear(): Observable<void> {
    return this.http.delete<void>(this.base).pipe(tap(() => this.itemCountSubject.next(0)));
  }

  /** Refreshes the badge count, e.g. after login. Silently ignores failures. */
  refresh(): void {
    this.get().subscribe({ error: () => {} });
  }

  /** Resets the badge count, e.g. after logout. */
  reset(): void {
    this.itemCountSubject.next(0);
  }

  private updateCount(cart: Cart): void {
    this.itemCountSubject.next(cart.items.reduce((sum, item) => sum + item.quantity, 0));
  }
}
