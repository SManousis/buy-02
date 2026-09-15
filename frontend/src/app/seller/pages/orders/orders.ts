import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil, timeout } from 'rxjs';
import { Order, OrderStatus, OrderService, nextOrderStatus } from '../../../shared/services/order';

const STATUS_FILTERS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: 'All orders' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'SHIPPED', label: 'Shipped' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

@Component({
  selector: 'app-seller-orders',
  standalone: false,
  templateUrl: './orders.html',
  styleUrl: './orders.scss',
})
export class SellerOrders implements OnInit, OnDestroy {
  orders: Order[] = [];
  loading = true;
  loadError = false;
  statusFilters = STATUS_FILTERS;
  selectedStatus: OrderStatus | '' = '';
  searchText = '';
  updatingId: string | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    private orderService: OrderService,
    private snack: MatSnackBar,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.loading = true;
    this.loadError = false;
    this.orderService.selling(this.selectedStatus || undefined)
      .pipe(timeout(10_000), takeUntil(this.destroy$))
      .subscribe({
        next: (orders) => {
          this.orders = orders;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.loadError = true;
          this.loading = false;
          this.changeDetector.detectChanges();
          this.snack.open('Orders could not be loaded. Try again.', 'Close', { duration: 4000, panelClass: 'snack-error' });
        },
      });
  }

  onStatusChange(status: string): void {
    this.selectedStatus = status as OrderStatus | '';
    this.load();
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

  itemCount(order: Order): number {
    return order.items.reduce((sum, item) => sum + item.quantity, 0);
  }

  statusClass(status: OrderStatus): string {
    return 'status-' + status.toLowerCase();
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

  private statusErrorMessage(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 400) return this.serverMessage(error) ?? 'That status change is not allowed from the order\'s current state.';
    if (status === 404) return 'This order no longer exists or is not yours.';
    if (status === 403) return 'You do not have permission to update this order.';
    if (status === 409) return this.serverMessage(error) ?? 'This order was just updated elsewhere. Refreshing…';
    if (status === 0) return 'Cannot reach the server. Check your connection.';
    return 'Could not update this order. Try again.';
  }

  private serverMessage(error: unknown): string | null {
    if (error instanceof HttpErrorResponse && typeof error.error?.message === 'string') {
      return error.error.message;
    }
    return null;
  }
}
