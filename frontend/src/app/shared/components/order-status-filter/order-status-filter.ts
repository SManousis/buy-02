import { Component, EventEmitter, Input, Output } from '@angular/core';
import { OrderStatus } from '../../services/order';

const STATUS_FILTERS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: 'All orders' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'SHIPPED', label: 'Shipped' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

/**
 * The labelled status dropdown used by the buyer and seller order lists.
 * Emits the chosen status, or an empty string for "All orders".
 */
@Component({
  selector: 'app-order-status-filter',
  standalone: false,
  templateUrl: './order-status-filter.html',
  styleUrl: './order-status-filter.scss',
})
export class OrderStatusFilter {
  @Input({ required: true }) value: OrderStatus | '' = '';
  @Output() valueChange = new EventEmitter<string>();

  readonly statusFilters = STATUS_FILTERS;
}
