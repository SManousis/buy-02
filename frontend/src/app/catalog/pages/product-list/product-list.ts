import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, takeUntil, timeout } from 'rxjs';
import { Product, ProductService } from '../../../shared/services/product';

@Component({
  selector: 'app-product-list',
  standalone: false,
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList implements OnInit, OnDestroy {
  products: Product[] = [];
  loading = true;
  error = false;

  private destroy$ = new Subject<void>();

  constructor(
    private productService: ProductService,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    this.loading = true;
    this.error = false;
    this.productService.getAll()
      .pipe(
        timeout(10_000),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (products) => {
          this.products = products;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.error = true;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
