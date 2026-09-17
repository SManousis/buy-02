import { ChangeDetectorRef, Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, takeUntil } from 'rxjs';
import { Order, OrderStatus, OrderService, nextOrderStatus } from '../../../shared/services/order';
import { serverMessage } from '../../../shared/services/http-error';
import { OrderListPageBase } from '../../../shared/components/order-list-page-base';

@Component({
  selector: 'app-seller-orders',
  standalone: false,
  templateUrl: './orders.html',
  styleUrl: './orders.scss',
})
export class SellerOrders extends OrderListPageBase {
  updatingId: string | null = null;

  constructor(
    orderService: OrderService,
    snack: MatSnackBar,
    changeDetector: ChangeDetectorRef,
  ) {
    super(orderService, snack, changeDetector);
  }

  get filteredOrders(): Order[] {
    const term = this.searchText.trim().toLowerCase();
    if (!term) return this.orders;
    return this.orders.filter(order =>
      order.id.toLowerCase().includes(term) ||
      order.buyerId.toLowerCase().includes(term) ||
      order.items.some(item => item.name.toLowerCase().includes(term)),
    );
  }

  nextStatus(order: Order): OrderStatus | null {
    return nextOrderStatus(order.status);
  }

  advance(order: Order): void {
    const next = this.nextStatus(order);
    if (!next || this.updatingId) return;
    this.updatingId = order.id;
    this.orderService.updateStatus(order.id, next)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (updated) => {
          this.updatingId = null;
          const index = this.orders.findIndex(o => o.id === updated.id);
          if (index !== -1) this.orders[index] = updated;
          this.changeDetector.detectChanges();
          this.snack.open(`Order marked as ${updated.status}.`, 'Close', { duration: 3000, panelClass: 'snack-success' });
        },
        error: (error) => {
          this.updatingId = null;
          this.changeDetector.detectChanges();
          this.snack.open(this.statusErrorMessage(error), 'Close', { duration: 4500, panelClass: 'snack-error' });
          this.load();
        },
      });
  }

  protected override fetch(): Observable<Order[]> {
    return this.orderService.selling(this.selectedStatus || undefined, this.searchText);
  }

  private statusErrorMessage(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 400) return serverMessage(error) ?? 'That status change is not allowed from the order\'s current state.';
    if (status === 404) return 'This order no longer exists or is not yours.';
    if (status === 403) return 'You do not have permission to update this order.';
    if (status === 409) return serverMessage(error) ?? 'This order was just updated elsewhere. Refreshing…';
    if (status === 0) return 'Cannot reach the server. Check your connection.';
    return 'Could not update this order. Try again.';
  }

}
