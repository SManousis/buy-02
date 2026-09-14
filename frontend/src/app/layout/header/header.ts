import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { AuthService } from '../../shared/services/auth';
import { MediaService } from '../../shared/services/media';

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

  private destroy$ = new Subject<void>();

  constructor(private auth: AuthService, private media: MediaService, private router: Router) {}

  ngOnInit(): void {
    this.auth.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.isLoggedIn = !!user;
        this.isSeller = user?.role === 'SELLER';
        this.username = user?.username ?? '';
      });
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
    this.router.navigate(['/']);
  }
}
