import { ChangeDetectorRef, Directive } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Order, OrderService, OrderStatus } from '../services/order';
import { LoadablePageBase } from './loadable-page-base';

/**
 * Behaviour shared by the buyer's order history and the seller's incoming
 * orders: a status filter that reloads the list, the per-row helpers, and the
 * snackbar shown when the list fails to load.
 */
@Directive()
export abstract class OrderListPageBase extends LoadablePageBase<Order[]> {
  orders: Order[] = [];
  selectedStatus: OrderStatus | '' = '';

  protected constructor(
    protected readonly orderService: OrderService,
    protected readonly snack: MatSnackBar,
    changeDetector: ChangeDetectorRef,
  ) {
    super(changeDetector);
  }

  onStatusChange(status: string): void {
    this.selectedStatus = status as OrderStatus | '';
    this.load();
  }

  itemCount(order: Order): number {
    return order.items.reduce((sum, item) => sum + item.quantity, 0);
  }

  statusClass(status: OrderStatus): string {
    return 'status-' + status.toLowerCase();
  }

  protected override onLoaded(orders: Order[]): void {
    this.orders = orders;
  }

  protected override onLoadError(): void {
    this.snack.open('Orders could not be loaded. Try again.', 'Close', { duration: 4000, panelClass: 'snack-error' });
  }
}
