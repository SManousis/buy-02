import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { CatalogRoutingModule } from './catalog-routing-module';
import { Home } from './pages/home/home';
import { ProductList } from './pages/product-list/product-list';
import { ProductDetail } from './pages/product-detail/product-detail';
import { ProductCard } from './components/product-card/product-card';

@NgModule({
  declarations: [Home, ProductList, ProductDetail, ProductCard],
  imports: [
    CommonModule,
    RouterModule,
    CatalogRoutingModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
})
export class CatalogModule {}
