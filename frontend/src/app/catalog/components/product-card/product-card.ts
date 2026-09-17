import { ChangeDetectorRef, Component, Input, OnDestroy } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { Product } from '../../../shared/services/product';
import { CartService } from '../../../shared/services/cart';
import { AuthService } from '../../../shared/services/auth';
import { environment } from '../../../../environments/environment';
import { addToCartErrorMessage } from '../../../shared/services/http-error';

@Component({
  selector: 'app-product-card',
  standalone: false,
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard implements OnDestroy {
  @Input() product!: Product;
  addingToCart = false;

  private destroy$ = new Subject<void>();

  constructor(
    private cartService: CartService,
    private auth: AuthService,
    private router: Router,
    private snack: MatSnackBar,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Mirrors the product detail page: buyers only, and only while stock remains. */
  get canAddToCart(): boolean {
    return this.auth.isLoggedIn() && !this.auth.hasRole('SELLER') && (this.product?.stock ?? 1) > 0;
  }

  addToCart(event: Event): void {
    event.stopPropagation();
    if (!this.canAddToCart || this.addingToCart) return;
    this.addingToCart = true;
    this.cartService.addItem(this.product.id, 1)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.addingToCart = false;
          this.changeDetector.detectChanges();
          this.snack
            .open(`${this.product.name} added to cart`, 'View cart', { duration: 3500, panelClass: 'snack-success' })
            .onAction()
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => this.router.navigate(['/cart']));
        },
        error: (error) => {
          this.addingToCart = false;
          this.changeDetector.detectChanges();
          this.snack.open(addToCartErrorMessage(error), 'Close', { duration: 4000, panelClass: 'snack-error' });
        },
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
