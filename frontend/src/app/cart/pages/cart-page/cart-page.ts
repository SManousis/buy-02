import { ChangeDetectorRef, Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, takeUntil } from 'rxjs';
import { Cart, CartItem, CartService } from '../../../shared/services/cart';
import { serverMessage } from '../../../shared/services/http-error';
import { LoadablePageBase } from '../../../shared/components/loadable-page-base';

@Component({
  selector: 'app-cart-page',
  standalone: false,
  templateUrl: './cart-page.html',
  styleUrl: './cart-page.scss',
})
export class CartPage extends LoadablePageBase<Cart> {
  cart: Cart | null = null;
  updatingProductId: string | null = null;

  constructor(
    private readonly cartService: CartService,
    private readonly snack: MatSnackBar,
    changeDetector: ChangeDetectorRef,
  ) {
    super(changeDetector);
  }

  get subtotal(): number {
    return this.cart?.subtotal ?? 0;
  }

  increment(productId: string, currentQuantity: number): void {
    this.changeQuantity(productId, currentQuantity + 1);
  }

  decrement(productId: string, currentQuantity: number): void {
    if (currentQuantity <= 1) {
      this.remove(productId);
      return;
    }
    this.changeQuantity(productId, currentQuantity - 1);
  }

  remove(productId: string): void {
    this.updatingProductId = productId;
    this.cartService.removeItem(productId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (cart) => {
          this.cart = cart;
          this.updatingProductId = null;
          this.changeDetector.detectChanges();
          this.snack.open('Item removed from cart.', 'Close', { duration: 2500, panelClass: 'snack-success' });
        },
        error: (error) => this.handleItemError(error),
      });
  }

  clear(): void {
    if (this.updatingProductId) return;
    this.updatingProductId = 'all';
    this.cartService.clear()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.updatingProductId = null;
          this.load();
          this.snack.open('Cart cleared.', 'Close', { duration: 2500, panelClass: 'snack-success' });
        },
        error: (error) => this.handleItemError(error),
      });
  }

  trackByProductId(_index: number, item: CartItem): string {
    return item.productId;
  }

  protected override fetch(): Observable<Cart> {
    return this.cartService.get();
  }

  protected override onLoaded(cart: Cart): void {
    this.cart = cart;
  }

  private changeQuantity(productId: string, quantity: number): void {
    if (this.updatingProductId) return;
    this.updatingProductId = productId;
    this.cartService.updateItem(productId, quantity)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (cart) => {
          this.cart = cart;
          this.updatingProductId = null;
          this.changeDetector.detectChanges();
        },
        error: (error) => this.handleItemError(error),
      });
  }

  private handleItemError(error: unknown): void {
    this.updatingProductId = null;
    this.changeDetector.detectChanges();
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    let message = 'Could not update your cart. Try again.';
    if (status === 404) message = 'That item is no longer in your cart. Refreshing…';
    else if (status === 409) message = serverMessage(error) ?? 'Not enough stock available for that quantity.';
    else if (status === 0) message = 'Cannot reach the server. Check your connection.';
    this.snack.open(message, 'Close', { duration: 4000, panelClass: 'snack-error' });
    this.load();
  }

}
