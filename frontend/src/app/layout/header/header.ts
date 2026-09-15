import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { AuthService } from '../../shared/services/auth';
import { MediaService } from '../../shared/services/media';
import { CartService } from '../../shared/services/cart';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header implements OnInit, OnDestroy {
  isLoggedIn = false;
  isSeller = false;
  username = '';
  avatarUrl: string | null = null;
  cartCount = 0;

  private destroy$ = new Subject<void>();

  constructor(private auth: AuthService, private media: MediaService, private router: Router, private carts: CartService) {}

  ngOnInit(): void {
    this.auth.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.isLoggedIn = !!user;
        this.isSeller = user?.role === 'SELLER';
        this.username = user?.username ?? '';
        if (user) this.carts.get().subscribe({ error: () => this.carts.resetCount() });
        else this.carts.resetCount();
      });
    this.carts.itemCount$.pipe(takeUntil(this.destroy$)).subscribe(count => this.cartCount = count);
    this.auth.currentProfile$
      .pipe(takeUntil(this.destroy$))
      .subscribe(profile => {
        this.avatarUrl = profile?.avatarMediaId ? this.media.getImageUrl(profile.avatarMediaId) : null;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  logout(): void {
    this.auth.logout();
    this.carts.resetCount();
    this.router.navigate(['/']);
  }
}
