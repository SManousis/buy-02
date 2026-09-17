import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { OrderDetailBody } from './components/order-detail-body/order-detail-body';
import { LoadState } from './components/load-state/load-state';
import { OrderStatusFilter } from './components/order-status-filter/order-status-filter';

@NgModule({
  declarations: [OrderDetailBody, LoadState, OrderStatusFilter],
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule],
  exports: [OrderDetailBody, LoadState, OrderStatusFilter],
})
export class SharedModule {}
