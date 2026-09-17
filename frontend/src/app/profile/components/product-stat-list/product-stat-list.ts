import { Component, Input } from '@angular/core';
import { ProductStat } from '../../../shared/services/order';

/**
 * A ranked product list for the profile analytics panels. `measure` decides
 * which figure is emphasised: money for "top products", quantity for
 * "most bought" and "best-selling".
 */
@Component({
  selector: 'app-product-stat-list',
  standalone: false,
  templateUrl: './product-stat-list.html',
  styleUrl: './product-stat-list.scss',
})
export class ProductStatList {
  @Input({ required: true }) heading = '';
  @Input({ required: true }) products: ProductStat[] = [];
  @Input({ required: true }) measure: 'amount' | 'quantity' = 'amount';
  @Input({ required: true }) quantityLabel = '';

  trackByProductId(_index: number, product: ProductStat): string {
    return product.productId;
  }
}
