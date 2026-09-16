import { ChangeDetectorRef, Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { takeUntil } from 'rxjs';
import { OrderStatus, OrderService, ReorderResponse } from '../../../shared/services/order';
import { CartService } from '../../../shared/services/cart';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { httpErrorMessage, serverMessage } from '../../../shared/services/http-error';
import { OrderDetailPageBase } from '../../../shared/components/order-detail-page-base';

const CANCELLABLE_STATUSES: readonly OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED'];

@Component({
  selector: 'app-order-detail',
  standalone: false,
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss',
})
export class OrderDetail extends OrderDetailPageBase {
  cancelling = false;
  reordering = false;

  constructor(
    route: ActivatedRoute,
    orderService: OrderService,
    private readonly cartService: CartService,
    private readonly router: Router,
    private readonly snack: MatSnackBar,
    private readonly dialog: MatDialog,
    changeDetector: ChangeDetectorRef,
  ) {
    super(route, orderService, changeDetector);
  }

  get canCancel(): boolean {
    return !!this.order && CANCELLABLE_STATUSES.includes(this.order.status);
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
    return httpErrorMessage(error, {
      404: 'This order no longer exists.',
      409: serverMessage(error) ?? 'This order can no longer be cancelled.',
      fallback: 'Could not cancel this order. Try again.',
    });
  }

  private reorderErrorMessage(error: unknown): string {
    return httpErrorMessage(error, {
      404: 'This order no longer exists.',
      fallback: 'Could not reorder these items. Try again.',
    });
  }
}
