import { ChangeDetectorRef, Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { Subject, takeUntil, timeout } from 'rxjs';
import { ProductService, Product } from '../../../shared/services/product';
import { environment } from '../../../../environments/environment';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-dashboard',
  standalone: false,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit, OnDestroy {
  products: Product[] = [];
  loading = true;
  loadError = false;
  deletingId: string | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    private productService: ProductService,
    private router: Router,
    private snack: MatSnackBar,
    private dialog: MatDialog,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadProducts();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadProducts(): void {
    this.loading = true;
    this.loadError = false;
    this.productService.getMyProducts()
      .pipe(
        timeout(10_000),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (list) => {
          this.products = list;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.loadError = true;
          this.loading = false;
          this.changeDetector.detectChanges();
          this.snack.open('Products could not be loaded. Try again.', 'Close', {
            duration: 4000,
            panelClass: 'snack-error',
          });
        },
      });
  }

  imageUrl(product: Product): string {
    if (product.imageIds?.length) {
      return environment.apiBaseUrl + '/media/images/' + product.imageIds[0];
    }
    return 'assets/placeholder.svg';
  }

  formatPrice(price: number): string {
    return new Intl.NumberFormat('el-GR', { style: 'currency', currency: 'EUR' }).format(price);
  }

  editProduct(id: string): void {
    this.router.navigate(['/seller/products/edit', id]);
  }

  manageMedia(id: string): void {
    this.router.navigate(['/seller/products', id, 'media']);
  }

  deleteProduct(product: Product): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { message: 'Delete "' + product.name + '"? This cannot be undone.' },
      width: '360px',
    });
    ref.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(confirmed => {
        if (!confirmed) return;
        this.deletingId = product.id;
        this.productService.delete(product.id)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.deletingId = null;
              this.snack.open('Product deleted.', 'Close', { duration: 3000, panelClass: 'snack-success' });
              this.loadProducts();
            },
            error: () => {
              this.deletingId = null;
              this.changeDetector.detectChanges();
              this.snack.open('Delete failed. Try again.', 'Close', { duration: 4000, panelClass: 'snack-error' });
            },
          });
      });
  }
}
