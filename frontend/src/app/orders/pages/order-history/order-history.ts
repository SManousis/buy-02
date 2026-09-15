import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil, timeout } from 'rxjs';
import { Order, OrderStatus, OrderService } from '../../../shared/services/order';

const STATUS_FILTERS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: 'All orders' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'SHIPPED', label: 'Shipped' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

@Component({
  selector: 'app-order-history',
  standalone: false,
  templateUrl: './order-history.html',
  styleUrl: './order-history.scss',
})
export class OrderHistory implements OnInit, OnDestroy {
  orders: Order[] = [];
  loading = true;
  loadError = false;
  statusFilters = STATUS_FILTERS;
  selectedStatus: OrderStatus | '' = '';

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
    this.orderService.mine(this.selectedStatus || undefined)
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

  itemCount(order: Order): number {
    return order.items.reduce((sum, item) => sum + item.quantity, 0);
  }

  statusClass(status: OrderStatus): string {
    return 'status-' + status.toLowerCase();
  }
}
