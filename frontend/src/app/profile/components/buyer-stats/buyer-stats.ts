import { ChangeDetectorRef, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { BuyerStats, OrderService } from '../../../shared/services/order';
import { LoadablePageBase } from '../../../shared/components/loadable-page-base';

/** Spending and favourite products for a buyer, from their non-cancelled orders. */
@Component({
  selector: 'app-buyer-stats',
  standalone: false,
  templateUrl: './buyer-stats.html',
  styleUrl: '../stats-panel.scss',
})
export class BuyerStatsPanel extends LoadablePageBase<BuyerStats> {
  stats: BuyerStats | null = null;

  constructor(
    private readonly orderService: OrderService,
    changeDetector: ChangeDetectorRef,
  ) {
    super(changeDetector);
  }

  protected override fetch(): Observable<BuyerStats> {
    return this.orderService.statsMine();
  }

  protected override onLoaded(stats: BuyerStats): void {
    this.stats = stats;
  }
}
