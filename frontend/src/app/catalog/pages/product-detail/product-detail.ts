import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Product, ProductService } from '../../../shared/services/product';
import { CartService } from '../../../shared/services/cart';
import { AuthService } from '../../../shared/services/auth';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

@Component({
  selector: 'app-product-detail',
  standalone: false,
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail implements OnInit {
  product: Product | null = null;
  loading = true;
  notFound = false;
  selectedImageIndex = 0;
  adding = false;

  constructor(
    private route: ActivatedRoute,
    private productService: ProductService,
    private changeDetector: ChangeDetectorRef,
    private carts: CartService,
    private auth: AuthService,
    private router: Router,
    private snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    const productId = this.route.snapshot.paramMap.get('id');
    if (!productId) {
      this.loading = false;
      this.notFound = true;
      return;
    }

    this.productService.getById(productId).subscribe({
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

  addToCart(): void {
    if (!this.product) return;
    if (!this.auth.isLoggedIn()) { this.router.navigate(['/auth/login'], { queryParams: { returnUrl: this.router.url } }); return; }
    this.adding = true;
    this.carts.add(this.product.id).subscribe({
      next: () => { this.adding = false; this.snackBar.open('Added to cart', 'View cart', { duration: 3500 }).onAction().subscribe(() => this.router.navigate(['/cart'])); this.changeDetector.detectChanges(); },
      error: error => { this.adding = false; this.snackBar.open(error?.error?.message ?? 'Could not add this product.', 'Close', { duration: 4500 }); this.changeDetector.detectChanges(); },
    });
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
