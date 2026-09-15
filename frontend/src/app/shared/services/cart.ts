import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
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
  id: string | null;
  items: CartItem[];
  subtotal: number;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly base = `${environment.apiBaseUrl}/cart`;
  private readonly countSubject = new BehaviorSubject(0);
  readonly itemCount$ = this.countSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  get(): Observable<Cart> {
    return this.sync(this.http.get<Cart>(this.base));
  }

  add(productId: string, quantity = 1): Observable<Cart> {
    return this.sync(this.http.post<Cart>(`${this.base}/items`, { productId, quantity }));
  }

  update(productId: string, quantity: number): Observable<Cart> {
    return this.sync(this.http.put<Cart>(`${this.base}/items/${encodeURIComponent(productId)}`, { quantity }));
  }

  remove(productId: string): Observable<Cart> {
    return this.sync(this.http.delete<Cart>(`${this.base}/items/${encodeURIComponent(productId)}`));
  }

  clear(): Observable<void> {
    return this.http.delete<void>(this.base).pipe(tap(() => this.countSubject.next(0)));
  }

  resetCount(): void { this.countSubject.next(0); }

  private sync(request: Observable<Cart>): Observable<Cart> {
    return request.pipe(tap(cart => this.countSubject.next(
      cart.items.reduce((total, item) => total + item.quantity, 0)
    )));
  }
}
