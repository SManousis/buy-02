import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './shared/guards/auth-guard';
import { RoleGuard } from './shared/guards/role-guard';

const routes: Routes = [
  {
    path: '',
    loadChildren: () => import('./catalog/catalog-module').then(m => m.CatalogModule)
  },
  {
    path: 'auth',
    loadChildren: () => import('./auth/auth-module').then(m => m.AuthModule)
  },
  {
    path: 'seller',
    canActivate: [AuthGuard, RoleGuard],
    data: { role: 'SELLER' },
    loadChildren: () => import('./seller/seller-module').then(m => m.SellerModule)
  },
  {
    path: 'profile',
    canActivate: [AuthGuard],
    loadChildren: () => import('./profile/profile-module').then(m => m.ProfileModule)
  },
  {
    path: 'cart',
    canActivate: [AuthGuard],
    loadChildren: () => import('./cart/cart-module').then(m => m.CartModule)
  },
  {
    path: 'orders',
    canActivate: [AuthGuard],
    loadChildren: () => import('./orders/orders-module').then(m => m.OrdersModule)
  },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { scrollPositionRestoration: 'enabled' })],
  exports: [RouterModule]
})
export class AppRoutingModule { }
