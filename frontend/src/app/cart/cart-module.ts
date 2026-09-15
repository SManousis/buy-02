import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CartRoutingModule } from './cart-routing-module';
import { CartPage } from './pages/cart-page/cart-page';

@NgModule({
  declarations: [CartPage],
  imports: [CommonModule, CartRoutingModule, MatIconModule, MatProgressSpinnerModule],
})
export class CartModule {}
