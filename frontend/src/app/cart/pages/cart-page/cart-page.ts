import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Cart, CartItem, CartService } from '../../../shared/services/cart';

@Component({ selector: 'app-cart-page', standalone: false, templateUrl: './cart-page.html', styleUrl: './cart-page.scss' })
export class CartPage implements OnInit {
  cart: Cart | null = null;
  loading = true;
  busyProductId: string | null = null;
  error = '';

  constructor(private readonly carts: CartService, private readonly changeDetector: ChangeDetectorRef) {}

  ngOnInit(): void { this.load(); }

  change(item: CartItem, delta: number): void {
    const quantity = item.quantity + delta;
    if (quantity < 0 || this.busyProductId) return;
    this.busyProductId = item.productId;
    this.error = '';
    this.carts.update(item.productId, quantity).subscribe({
      next: cart => this.finish(cart),
      error: error => this.fail(error?.error?.message ?? 'Could not update the cart.'),
    });
  }

  remove(item: CartItem): void {
    if (this.busyProductId) return;
    this.busyProductId = item.productId;
    this.carts.remove(item.productId).subscribe({
      next: cart => this.finish(cart),
      error: error => this.fail(error?.error?.message ?? 'Could not remove the item.'),
    });
  }

  clear(): void {
    if (this.busyProductId) return;
    this.busyProductId = 'all';
    this.carts.clear().subscribe({
      next: () => this.load(),
      error: () => this.fail('Could not clear the cart.'),
    });
  }

  lineTotal(item: CartItem): number { return item.unitPriceSnapshot * item.quantity; }
  trackByProduct(_: number, item: CartItem): string { return item.productId; }

  private load(): void {
    this.loading = true;
    this.carts.get().subscribe({ next: cart => { this.cart = cart; this.loading = false; this.busyProductId = null; this.changeDetector.detectChanges(); }, error: () => { this.loading = false; this.fail('Could not load the cart.'); } });
  }
  private finish(cart: Cart): void { this.cart = cart; this.busyProductId = null; this.changeDetector.detectChanges(); }
  private fail(message: string): void { this.error = message; this.busyProductId = null; this.changeDetector.detectChanges(); }
}
