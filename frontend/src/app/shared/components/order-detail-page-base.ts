import { ChangeDetectorRef, Directive, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { Order, OrderService, OrderStatus } from '../services/order';

/**
 * Loading behaviour common to the buyer and seller order detail pages: read the
 * order id from the route, fetch it, and translate a failed fetch into the
 * "not found" state. The service masks orders that belong to someone else as
 * 404, so both roles treat any error the same way.
 *
 * Decorated with @Directive() because Angular requires that on a base class
 * that declares lifecycle hooks and constructor injection.
 */
@Directive()
export abstract class OrderDetailPageBase implements OnInit, OnDestroy {
  order: Order | null = null;
  loading = true;
  notFound = false;

  protected readonly destroy$ = new Subject<void>();

  protected constructor(
    protected readonly route: ActivatedRoute,
    protected readonly orderService: OrderService,
    protected readonly changeDetector: ChangeDetectorRef,
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

  statusClass(status: OrderStatus): string {
    return 'status-' + status.toLowerCase();
  }

  get itemCount(): number {
    return this.order?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;
  }

  protected load(id: string): void {
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
}
