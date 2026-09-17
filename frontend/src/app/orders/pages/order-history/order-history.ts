import { ChangeDetectorRef, Component } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { Order, OrderService } from '../../../shared/services/order';
import { OrderListPageBase } from '../../../shared/components/order-list-page-base';

@Component({
  selector: 'app-order-history',
  standalone: false,
  templateUrl: './order-history.html',
  styleUrl: './order-history.scss',
})
export class OrderHistory extends OrderListPageBase {
  constructor(
    orderService: OrderService,
    snack: MatSnackBar,
    changeDetector: ChangeDetectorRef,
  ) {
    super(orderService, snack, changeDetector);
  }

  protected override fetch(): Observable<Order[]> {
    return this.orderService.mine(this.selectedStatus || undefined);
  }
}
