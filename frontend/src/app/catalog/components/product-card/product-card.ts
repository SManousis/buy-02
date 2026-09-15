import { Component, Input } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Product } from '../../../shared/services/product';
import { CartService } from '../../../shared/services/cart';
import { AuthService } from '../../../shared/services/auth';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-product-card',
  standalone: false,
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
  @Input() product!: Product;
  adding = false;

  constructor(private carts: CartService, private auth: AuthService, private router: Router, private snackBar: MatSnackBar) {}

  addToCart(event: Event): void {
    event.stopPropagation();
    if (!this.auth.isLoggedIn()) { this.router.navigate(['/auth/login'], { queryParams: { returnUrl: `/products/${this.product.id}` } }); return; }
    if (this.product.stock === 0 || this.adding) return;
    this.adding = true;
    this.carts.add(this.product.id).subscribe({
      next: () => { this.adding = false; this.snackBar.open(`${this.product.name} added to cart`, 'View cart', { duration: 3500 }).onAction().subscribe(() => this.router.navigate(['/cart'])); },
      error: error => { this.adding = false; this.snackBar.open(error?.error?.message ?? 'Could not add this product.', 'Close', { duration: 4500 }); },
    });
  }

  get imageUrl(): string {
    if (this.product.imageIds?.length) {
      const id = this.product.imageIds[0];
      return `${environment.apiBaseUrl}/media/images/${id}`;
    }
    return 'assets/placeholder.svg';
  }

  get formattedPrice(): string {
    return new Intl.NumberFormat('el-GR', { style: 'currency', currency: 'EUR' }).format(this.product.price);
  }
}
