import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Product, ProductService } from '../../../shared/services/product';

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

  constructor(
    private route: ActivatedRoute,
    private productService: ProductService,
    private changeDetector: ChangeDetectorRef,
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
