import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { Order, OrderStatus, OrderService, nextOrderStatus } from '../../../shared/services/order';

@Component({
  selector: 'app-seller-order-detail',
  standalone: false,
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss',
})
export class SellerOrderDetail implements OnInit, OnDestroy {
  order: Order | null = null;
  loading = true;
  notFound = false;
  updating = false;

  private destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private orderService: OrderService,
    private router: Router,
    private snack: MatSnackBar,
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

  get itemCount(): number {
    return this.order?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;
  }

  get nextStatus(): OrderStatus | null {
    return this.order ? nextOrderStatus(this.order.status) : null;
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
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 400) return this.serverMessage(error) ?? 'That status change is not allowed from the order\'s current state.';
    if (status === 404) return 'This order no longer exists or is not yours.';
    if (status === 403) return 'You do not have permission to update this order.';
    if (status === 409) return this.serverMessage(error) ?? 'This order was just updated elsewhere.';
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
