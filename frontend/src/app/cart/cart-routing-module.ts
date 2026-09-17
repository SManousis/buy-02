import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CartPage } from './pages/cart-page/cart-page';
import { Checkout } from './pages/checkout/checkout';

const routes: Routes = [
  { path: '', component: CartPage },
  { path: 'checkout', component: Checkout },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CartRoutingModule {}
