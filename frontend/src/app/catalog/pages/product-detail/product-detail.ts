import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Product, ProductService } from '../../../shared/services/product';
import { AuthService } from '../../../shared/services/auth';
import { CartService } from '../../../shared/services/cart';

@Component({
  selector: 'app-product-detail',
  standalone: false,
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail implements OnInit, OnDestroy {
  product: Product | null = null;
  loading = true;
  notFound = false;
  selectedImageIndex = 0;
  quantity = 1;
  addingToCart = false;

  private destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private productService: ProductService,
    private changeDetector: ChangeDetectorRef,
    private cartService: CartService,
    private auth: AuthService,
    private snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    const productId = this.route.snapshot.paramMap.get('id');
    if (!productId) {
      this.loading = false;
      this.notFound = true;
      return;
    }

    this.productService.getById(productId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (product) => {
          this.product = product;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.notFound = true;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get canAddToCart(): boolean {
    return this.auth.isLoggedIn() && !this.auth.hasRole('SELLER') && (this.product?.stock ?? 1) > 0;
  }

  incrementQuantity(): void {
    const max = this.product?.stock ?? Number.MAX_SAFE_INTEGER;
    if (this.quantity < max) this.quantity++;
  }

  decrementQuantity(): void {
    if (this.quantity > 1) this.quantity--;
  }

  addToCart(): void {
    if (!this.product || this.addingToCart) return;
    this.addingToCart = true;
    this.cartService.addItem(this.product.id, this.quantity)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.addingToCart = false;
          this.changeDetector.detectChanges();
          this.snack.open('Added to cart!', 'Close', { duration: 2500, panelClass: 'snack-success' });
        },
        error: (error) => {
          this.addingToCart = false;
          this.changeDetector.detectChanges();
          this.snack.open(this.addToCartErrorMessage(error), 'Close', { duration: 4000, panelClass: 'snack-error' });
        },
      });
  }

  private addToCartErrorMessage(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 409) {
      const message = error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : null;
      return message ?? 'Not enough stock available for that quantity.';
    }
    if (status === 404) return 'This product is no longer available.';
    if (status === 0) return 'Cannot reach the server. Check your connection.';
    return 'Could not add this item to your cart. Try again.';
  }

  selectImage(index: number): void {
    this.selectedImageIndex = index;
  }

  imageUrl(imageId: string): string {
    return `${environment.apiBaseUrl}/media/images/${imageId}`;
  }

  get selectedImageUrl(): string | null {
    const imageId = this.product?.imageIds?.[this.selectedImageIndex];
    return imageId ? this.imageUrl(imageId) : null;
  }

  get formattedPrice(): string {
    return new Intl.NumberFormat('el-GR', {
      style: 'currency',
      currency: 'EUR',
    }).format(this.product?.price ?? 0);
  }

  get availability(): string {
    if (this.product?.stock === 0) return 'Out of stock';
    if (this.product?.stock == null) return 'Available';
    return `${this.product.stock} in stock`;
  }
}

