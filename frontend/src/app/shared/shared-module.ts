import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { OrderDetailBody } from './components/order-detail-body/order-detail-body';

@NgModule({
  declarations: [OrderDetailBody],
  imports: [CommonModule],
  exports: [OrderDetailBody],
})
export class SharedModule {}
