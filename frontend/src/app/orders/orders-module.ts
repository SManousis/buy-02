import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule } from '@angular/material/dialog';

import { OrdersRoutingModule } from './orders-routing-module';
import { OrderHistory } from './pages/order-history/order-history';
import { OrderDetail } from './pages/order-detail/order-detail';

@NgModule({
  declarations: [OrderHistory, OrderDetail],
  imports: [
    CommonModule,
    RouterModule,
    OrdersRoutingModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatDialogModule,
  ],
})
export class OrdersModule {}
