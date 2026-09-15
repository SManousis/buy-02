import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { Subject, takeUntil } from 'rxjs';
import { Order, OrderStatus, OrderService, ReorderResponse } from '../../../shared/services/order';
import { CartService } from '../../../shared/services/cart';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog';

const CANCELLABLE_STATUSES: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED'];

@Component({
  selector: 'app-order-detail',
  standalone: false,
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss',
})
export class OrderDetail implements OnInit, OnDestroy {
  order: Order | null = null;
  loading = true;
  notFound = false;
  cancelling = false;
  reordering = false;

  private destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private orderService: OrderService,
    private cartService: CartService,
    private router: Router,
    private snack: MatSnackBar,
    private dialog: MatDialog,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.loading = false;
      this.notFound = true;
      return;
    }
    this.load(id);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private load(id: string): void {
    this.loading = true;
    this.notFound = false;
    this.orderService.getById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (order) => {
          this.order = order;
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

  get canCancel(): boolean {
    return !!this.order && CANCELLABLE_STATUSES.includes(this.order.status);
  }

  get itemCount(): number {
    return this.order?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;
  }

  statusClass(status: OrderStatus): string {
    return 'status-' + status.toLowerCase();
  }

  isStatusReached(status: OrderStatus): boolean {
    if (!this.order) return false;
    if (this.order.status === 'CANCELLED') return status === 'CANCELLED';
    const chain: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];
    return chain.indexOf(status) <= chain.indexOf(this.order.status);
  }

  cancel(): void {
    if (!this.order || this.cancelling) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { message: 'Cancel this order? This cannot be undone.', confirmLabel: 'Cancel order', cancelLabel: 'Keep order' },
      width: '360px',
    });
    ref.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(confirmed => {
        if (!confirmed || !this.order) return;
        this.cancelling = true;
        this.orderService.cancel(this.order.id)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: (order) => {
              this.order = order;
              this.cancelling = false;
              this.changeDetector.detectChanges();
              this.snack.open('Order cancelled.', 'Close', { duration: 3000, panelClass: 'snack-success' });
            },
            error: (error) => {
              this.cancelling = false;
              this.changeDetector.detectChanges();
              this.snack.open(this.cancelErrorMessage(error), 'Close', { duration: 4000, panelClass: 'snack-error' });
            },
          });
      });
  }

  reorder(): void {
    if (!this.order || this.reordering) return;
    this.reordering = true;
    this.orderService.reorder(this.order.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.reordering = false;
          this.changeDetector.detectChanges();
          this.cartService.refresh();
          this.onReorderSuccess(response);
        },
        error: (error) => {
          this.reordering = false;
          this.changeDetector.detectChanges();
          this.snack.open(this.reorderErrorMessage(error), 'Close', { duration: 4000, panelClass: 'snack-error' });
        },
      });
  }

  private onReorderSuccess(response: ReorderResponse): void {
    if (response.unavailableItems.length === 0) {
      this.snack.open('All items added to your cart!', 'Close', { duration: 3000, panelClass: 'snack-success' });
    } else {
      const names = response.unavailableItems.map(i => i.name).join(', ');
      this.snack.open(`Some items could not be re-added: ${names}`, 'Close', { duration: 6000, panelClass: 'snack-error' });
    }
    this.router.navigate(['/cart']);
  }

  private cancelErrorMessage(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 409) return this.serverMessage(error) ?? 'This order can no longer be cancelled.';
    if (status === 404) return 'This order no longer exists.';
    if (status === 0) return 'Cannot reach the server. Check your connection.';
    return 'Could not cancel this order. Try again.';
  }

  private reorderErrorMessage(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 404) return 'This order no longer exists.';
    if (status === 0) return 'Cannot reach the server. Check your connection.';
    return 'Could not reorder these items. Try again.';
  }

  private serverMessage(error: unknown): string | null {
    if (error instanceof HttpErrorResponse && typeof error.error?.message === 'string') {
      return error.error.message;
    }
    return null;
  }
}
