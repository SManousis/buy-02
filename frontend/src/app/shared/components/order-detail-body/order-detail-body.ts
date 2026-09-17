import { Component, Input } from '@angular/core';
import { Order, OrderStatus } from '../../services/order';

/**
 * The parts of an order detail view that a buyer and a seller see identically:
 * the status timeline, the item list, the status history, and the shipping and
 * payment cards. Role-specific actions are projected into the sidebar so each
 * page keeps its own buttons.
 */
@Component({
  selector: 'app-order-detail-body',
  standalone: false,
  templateUrl: './order-detail-body.html',
  styleUrl: './order-detail-body.scss',
})
export class OrderDetailBody {
  @Input({ required: true }) order!: Order;

  statusClass(status: OrderStatus): string {
    return 'status-' + status.toLowerCase();
  }

  isStatusReached(status: OrderStatus): boolean {
    if (!this.order) return false;
    if (this.order.status === 'CANCELLED') return status === 'CANCELLED';
    const chain: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];
    return chain.indexOf(status) <= chain.indexOf(this.order.status);
  }
}
