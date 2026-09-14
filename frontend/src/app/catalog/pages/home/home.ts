import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { ProductService, Product } from '../../../shared/services/product';
import { AuthService } from '../../../shared/services/auth';

@Component({
  selector: 'app-home',
  standalone: false,
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit, OnDestroy {
  products: Product[] = [];
  loading = true;
  error = false;
  isLoggedIn = false;

  private destroy$ = new Subject<void>();

  constructor(
    private productService: ProductService,
    private auth: AuthService,
    private router: Router,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.auth.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe((user) => {
        this.isLoggedIn = !!user;
        if (user?.role === 'SELLER') {
          this.router.navigate(['/seller']);
        }
      });

    this.productService.getAll()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: list => {
          this.products = list.slice(0, 8);
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.error = true;
          this.changeDetector.detectChanges();
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
