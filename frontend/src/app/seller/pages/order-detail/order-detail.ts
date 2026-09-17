import { ChangeDetectorRef, Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { takeUntil } from 'rxjs';
import { OrderStatus, OrderService, nextOrderStatus } from '../../../shared/services/order';
import { httpErrorMessage, serverMessage } from '../../../shared/services/http-error';
import { OrderDetailPageBase } from '../../../shared/components/order-detail-page-base';

@Component({
  selector: 'app-seller-order-detail',
  standalone: false,
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss',
})
export class SellerOrderDetail extends OrderDetailPageBase {
  updating = false;

  constructor(
    route: ActivatedRoute,
    orderService: OrderService,
    private readonly router: Router,
    private readonly snack: MatSnackBar,
    changeDetector: ChangeDetectorRef,
  ) {
    super(route, orderService, changeDetector);
  }

  get nextStatus(): OrderStatus | null {
    return this.order ? nextOrderStatus(this.order.status) : null;
  }

  advance(): void {
    const next = this.nextStatus;
    if (!this.order || !next || this.updating) return;
    this.updating = true;
    this.orderService.updateStatus(this.order.id, next)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (order) => {
          this.order = order;
          this.updating = false;
          this.changeDetector.detectChanges();
          this.snack.open(`Order marked as ${order.status}.`, 'Close', { duration: 3000, panelClass: 'snack-success' });
        },
        error: (error) => {
          this.updating = false;
          this.changeDetector.detectChanges();
          this.snack.open(this.statusErrorMessage(error), 'Close', { duration: 4500, panelClass: 'snack-error' });
        },
      });
  }

  back(): void {
    this.router.navigate(['/seller/orders']);
  }

  private statusErrorMessage(error: unknown): string {
    return httpErrorMessage(error, {
      400: serverMessage(error) ?? 'That status change is not allowed from the order\'s current state.',
      403: 'You do not have permission to update this order.',
      404: 'This order no longer exists or is not yours.',
      409: serverMessage(error) ?? 'This order was just updated elsewhere.',
      fallback: 'Could not update this order. Try again.',
    });
  }
}
