import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Dashboard } from './pages/dashboard/dashboard';
import { ProductForm } from './pages/product-form/product-form';
import { ProductMedia } from './pages/product-media/product-media';

const routes: Routes = [
  { path: '',           component: Dashboard },
  { path: 'products/new',      component: ProductForm },
  { path: 'products/edit/:id', component: ProductForm },
  { path: 'products/:id/media', component: ProductMedia },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SellerRoutingModule {}
