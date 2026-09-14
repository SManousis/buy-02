import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { Pipe, PipeTransform } from '@angular/core';
import { SellerRoutingModule } from './seller-routing-module';
import { Dashboard } from './pages/dashboard/dashboard';
import { ProductForm } from './pages/product-form/product-form';
import { ProductMedia } from './pages/product-media/product-media';
import { ConfirmDialogComponent } from '../shared/components/confirm-dialog/confirm-dialog';
import { Product } from '../shared/services/product';

@Pipe({ name: 'activeCount', standalone: false })
export class ActiveCountPipe implements PipeTransform {
  transform(products: Product[]): number {
    return products.filter((p) => (p.stock ?? 1) > 0).length;
  }
}

@NgModule({
  declarations: [Dashboard, ProductForm, ProductMedia, ActiveCountPipe, ConfirmDialogComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    SellerRoutingModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatChipsModule,
    MatDialogModule,
  ],
})
export class SellerModule {}
