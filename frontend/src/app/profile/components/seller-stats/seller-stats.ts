import { ChangeDetectorRef, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { OrderService, SellerStats } from '../../../shared/services/order';
import { LoadablePageBase } from '../../../shared/components/loadable-page-base';

/** Revenue and best-selling products for a seller, from their non-cancelled orders. */
@Component({
  selector: 'app-seller-stats',
  standalone: false,
  templateUrl: './seller-stats.html',
  styleUrl: '../stats-panel.scss',
})
export class SellerStatsPanel extends LoadablePageBase<SellerStats> {
  stats: SellerStats | null = null;

  constructor(
    private readonly orderService: OrderService,
    changeDetector: ChangeDetectorRef,
  ) {
    super(changeDetector);
  }

  protected override fetch(): Observable<SellerStats> {
    return this.orderService.statsSelling();
  }

  protected override onLoaded(stats: SellerStats): void {
    this.stats = stats;
  }
}
