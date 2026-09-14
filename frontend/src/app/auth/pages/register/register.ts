import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil, throttleTime } from 'rxjs';
import { AuthService } from '../../../shared/services/auth';

function passwordMatch(ctrl: AbstractControl): ValidationErrors | null {
  const pw = ctrl.get('password')?.value;
  const confirm = ctrl.get('confirmPassword')?.value;
  return pw && confirm && pw !== confirm ? { passwordMismatch: true } : null;
}

function notBlank(ctrl: AbstractControl): ValidationErrors | null {
  return typeof ctrl.value === 'string' && ctrl.value.trim().length === 0 ? { whitespace: true } : null;
}

@Component({
  selector: 'app-register',
  standalone: false,
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register implements OnDestroy {
  form: FormGroup;
  loading = false;
  hidePassword = true;
  hideConfirm  = true;

  private destroy$ = new Subject<void>();
  private submitTrigger$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router,
    private snack: MatSnackBar,
  ) {
    this.form = this.fb.group({
      username:        ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50), notBlank]],
      email:           ['', [Validators.required, Validators.email]],
      password:        ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
      role:            ['CLIENT', Validators.required],
    }, { validators: passwordMatch });

    // Throttle: ignore repeated taps within 3 s
    this.submitTrigger$
      .pipe(throttleTime(3000), takeUntil(this.destroy$))
      .subscribe(() => this.doRegister());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submit(): void {
    if (this.form.invalid || this.loading) return;
    this.submitTrigger$.next();
  }

  private doRegister(): void {
    this.loading = true;
    const { username, password, email, role } = this.form.value as {
      username: string; password: string; email: string; role: string;
    };
    this.auth.register(username.trim(), password, email, role)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (user) => {
          this.loading = false;
          this.snack.open('Account created! Welcome.', 'Close', { duration: 3000, panelClass: 'snack-success' });
          this.router.navigate(user.role === 'SELLER' ? ['/seller'] : ['/']);
        },
        error: (err) => {
          this.loading = false;
          const msg = err.status === 409
            ? 'Username or email already taken.'
            : err.status === 0
              ? 'Cannot reach server. Check your connection.'
              : 'Registration failed. Try again.';
          this.snack.open(msg, 'Close', { duration: 4000, panelClass: 'snack-error' });
        },
      });
  }
}
