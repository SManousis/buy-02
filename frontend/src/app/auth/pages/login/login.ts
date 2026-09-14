import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil, throttleTime } from 'rxjs';
import { AuthService } from '../../../shared/services/auth';

@Component({
  selector: 'app-login',
  standalone: false,
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnDestroy {
  form: FormGroup;
  loading = false;
  hidePassword = true;

  private destroy$ = new Subject<void>();
  private submitTrigger$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router,
    private snack: MatSnackBar,
  ) {
    this.form = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
    });

    // Throttle: ignore repeated taps within 2 s
    this.submitTrigger$
      .pipe(throttleTime(2000), takeUntil(this.destroy$))
      .subscribe(() => this.doLogin());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submit(): void {
    if (this.form.invalid || this.loading) return;
    this.submitTrigger$.next();
  }

  private doLogin(): void {
    this.loading = true;
    const { email, password } = this.form.value as { email: string; password: string };
    this.auth.login(email, password)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (user) => {
          this.loading = false;
          this.router.navigate(user.role === 'SELLER' ? ['/seller'] : ['/']);
        },
        error: (err) => {
          this.loading = false;
          const msg = err.status === 401
            ? 'Invalid email or password.'
            : err.status === 0
              ? 'Cannot reach server. Check your connection.'
              : 'Login failed. Try again.';
          this.snack.open(msg, 'Close', { duration: 4000, panelClass: 'snack-error' });
        },
      });
  }
}
