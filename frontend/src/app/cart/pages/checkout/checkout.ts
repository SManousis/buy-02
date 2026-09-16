import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil, timeout } from 'rxjs';
import { Cart, CartService } from '../../../shared/services/cart';
import { CheckoutResponse, OrderService } from '../../../shared/services/order';
import { serverMessage } from '../../../shared/services/http-error';

@Component({
  selector: 'app-checkout',
  standalone: false,
  templateUrl: './checkout.html',
  styleUrl: './checkout.scss',
})
export class Checkout implements OnInit, OnDestroy {
  form: FormGroup;
  cart: Cart | null = null;
  loading = true;
  loadError = false;
  submitting = false;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private cartService: CartService,
    private orderService: OrderService,
    private router: Router,
    private snack: MatSnackBar,
    private changeDetector: ChangeDetectorRef,
  ) {
    this.form = this.fb.group({
      line1: ['', [Validators.required, Validators.maxLength(120)]],
      city: ['', [Validators.required, Validators.maxLength(80)]],
      postalCode: ['', [Validators.required, Validators.maxLength(20)]],
      country: ['', [Validators.required, Validators.maxLength(80)]],
      paymentMethod: ['CASH_ON_DELIVERY', [Validators.required]],
    });
  }

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.loading = true;
    this.loadError = false;
    this.cartService.get()
      .pipe(timeout(10_000), takeUntil(this.destroy$))
      .subscribe({
        next: (cart) => {
          this.cart = cart;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.loadError = true;
          this.loading = false;
          this.changeDetector.detectChanges();
        },
      });
  }

  get subtotal(): number {
    return this.cart?.subtotal ?? 0;
  }

  submit(): void {
    if (this.form.invalid || this.submitting) return;
    this.submitting = true;
    const { line1, city, postalCode, country, paymentMethod } = this.form.value as {
      line1: string; city: string; postalCode: string; country: string; paymentMethod: 'CASH_ON_DELIVERY';
    };

    this.orderService.checkout({
      shippingAddress: { line1: line1.trim(), city: city.trim(), postalCode: postalCode.trim(), country: country.trim() },
      paymentMethod,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (response) => this.onCheckoutSuccess(response),
      error: (error) => this.onCheckoutError(error),
    });
  }

  private onCheckoutSuccess(response: CheckoutResponse): void {
    this.submitting = false;
    this.cartService.reset();
    const count = response.orders.length;
    this.snack.open(
      count === 1 ? 'Order placed successfully!' : `${count} orders placed successfully!`,
      'Close',
      { duration: 4000, panelClass: 'snack-success' },
    );
    this.router.navigate(['/']);
  }

  private onCheckoutError(error: unknown): void {
    this.submitting = false;
    this.changeDetector.detectChanges();
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    let message = 'Could not place your order. Try again.';
    if (status === 400) message = serverMessage(error) ?? 'Your cart is empty. Add items before checking out.';
    else if (status === 409) message = serverMessage(error) ?? 'One of your items is no longer available at that quantity.';
    else if (status === 503) message = 'Product service is temporarily unavailable. Try again shortly.';
    else if (status === 0) message = 'Cannot reach the server. Check your connection.';
    this.snack.open(message, 'Close', { duration: 5000, panelClass: 'snack-error' });
    if (status === 400 || status === 409) this.load();
  }

}
