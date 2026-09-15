import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CartPage } from './pages/cart-page/cart-page';

const routes: Routes = [{ path: '', component: CartPage }];

@NgModule({ imports: [RouterModule.forChild(routes)], exports: [RouterModule] })
export class CartRoutingModule {}
