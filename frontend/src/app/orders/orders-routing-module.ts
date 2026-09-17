import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { OrderHistory } from './pages/order-history/order-history';
import { OrderDetail } from './pages/order-detail/order-detail';

const routes: Routes = [
  { path: '', component: OrderHistory },
  { path: ':id', component: OrderDetail },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OrdersRoutingModule {}
