import { Component, Input } from '@angular/core';
import { Product } from '../../../shared/services/product';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-product-card',
  standalone: false,
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
  @Input() product!: Product;

  get imageUrl(): string {
    if (this.product.imageIds?.length) {
      const id = this.product.imageIds[0];
      return `${environment.apiBaseUrl}/media/images/${id}`;
    }
    return 'assets/placeholder.svg';
  }

  get formattedPrice(): string {
    return new Intl.NumberFormat('el-GR', { style: 'currency', currency: 'EUR' }).format(this.product.price);
  }
}
