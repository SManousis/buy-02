import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';

import { CartRoutingModule } from './cart-routing-module';
import { CartPage } from './pages/cart-page/cart-page';
import { Checkout } from './pages/checkout/checkout';
import { SharedModule } from '../shared/shared-module';

@NgModule({
  declarations: [CartPage, Checkout],
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    CartRoutingModule,
    SharedModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
  ],
})
export class CartModule {}
