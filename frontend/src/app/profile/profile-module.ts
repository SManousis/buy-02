import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ProfileRoutingModule } from './profile-routing-module';
import { Profile } from './pages/profile/profile';
import { BuyerStatsPanel } from './components/buyer-stats/buyer-stats';
import { SellerStatsPanel } from './components/seller-stats/seller-stats';
import { ProductStatList } from './components/product-stat-list/product-stat-list';
import { SharedModule } from '../shared/shared-module';

@NgModule({
  declarations: [Profile, BuyerStatsPanel, SellerStatsPanel, ProductStatList],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ProfileRoutingModule,
    SharedModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
})
export class ProfileModule {}
