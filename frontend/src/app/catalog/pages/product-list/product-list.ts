import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, takeUntil, timeout } from 'rxjs';
import { Product, ProductFilters, ProductService } from '../../../shared/services/product';

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
  filters: ProductFilters = { sort: 'newest', inStock: false, page: 0, size: 24 };
  private filterTimer?: ReturnType<typeof setTimeout>;

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
    this.productService.getAll(this.filters)
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

  applyFilters(): void {
    clearTimeout(this.filterTimer);
    this.filters.page = 0;
    this.filterTimer = setTimeout(() => this.loadProducts(), 300);
  }

  clearFilters(): void {
    this.filters = { sort: 'newest', inStock: false, page: 0, size: 24 };
    this.loadProducts();
  }

  get hasActiveFilters(): boolean {
    return Boolean(this.filters.q?.trim() || this.filters.sellerId?.trim()
      || this.filters.minPrice !== undefined || this.filters.maxPrice !== undefined || this.filters.inStock);
  }

  get canLoadNext(): boolean {
    return this.products.length === (this.filters.size ?? 24);
  }

  changePage(direction: -1 | 1): void {
    this.filters.page = Math.max(0, (this.filters.page ?? 0) + direction);
    this.loadProducts();
  }

  ngOnDestroy(): void {
    clearTimeout(this.filterTimer);
    this.destroy$.next();
    this.destroy$.complete();
  }
}
