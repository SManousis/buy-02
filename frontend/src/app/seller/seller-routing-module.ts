import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Dashboard } from './pages/dashboard/dashboard';
import { ProductForm } from './pages/product-form/product-form';
import { ProductMedia } from './pages/product-media/product-media';
import { SellerOrders } from './pages/orders/orders';
import { SellerOrderDetail } from './pages/order-detail/order-detail';

const routes: Routes = [
  { path: '',           component: Dashboard },
  { path: 'products/new',      component: ProductForm },
  { path: 'products/edit/:id', component: ProductForm },
  { path: 'products/:id/media', component: ProductMedia },
  { path: 'orders',      component: SellerOrders },
  { path: 'orders/:id',  component: SellerOrderDetail },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SellerRoutingModule {}
