import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { ProductService, Product } from '../../../shared/services/product';
import { Subject, takeUntil, timeout } from 'rxjs';

function notBlank(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length === 0 ? { whitespace: true } : null;
}

@Component({
  selector: 'app-product-form',
  standalone: false,
  templateUrl: './product-form.html',
  styleUrl: './product-form.scss',
})
export class ProductForm implements OnInit, OnDestroy {
  form: FormGroup;
  editId: string | null = null;
  loading = false;
  saving = false;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private productService: ProductService,
    private route: ActivatedRoute,
    private router: Router,
    private snack: MatSnackBar,
    private changeDetector: ChangeDetectorRef,
  ) {
    this.form = this.fb.group({
      name:        ['', [Validators.required, Validators.maxLength(120), notBlank]],
      description: ['', [Validators.required, Validators.maxLength(2000), notBlank]],
      price:       [null, [Validators.required, Validators.min(0.01)]],
      stock:       [null, [Validators.min(0)]],
      imageIds:    [[]],
    });
  }

  ngOnInit(): void {
    this.editId = this.route.snapshot.paramMap.get('id');
    if (this.editId) {
      this.loading = true;
      this.productService.getById(this.editId).pipe(timeout(10_000), takeUntil(this.destroy$)).subscribe({
        next: (p) => {
          this.form.patchValue({ ...p, imageIds: p.imageIds ?? [] });
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.changeDetector.detectChanges();
          this.snack.open('Product not found.', 'Close', { duration: 3000, panelClass: 'snack-error' });
          this.router.navigate(['/seller']);
        },
      });
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submit(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;
    const raw = this.form.value as {
      name: string; description: string; price: number;
      stock: number; imageIds: string[];
    };
    const payload = {
      ...raw,
      name: raw.name.trim(),
      description: raw.description.trim(),
      imageIds: raw.imageIds ?? [],
    };

    const req = this.editId
      ? this.productService.update(this.editId, payload)
      : this.productService.create(payload);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.saving = false;
        this.snack.open(this.editId ? 'Product updated!' : 'Product created!', 'Close', {
          duration: 3000, panelClass: 'snack-success',
        });
        this.router.navigate(['/seller']);
      },
      error: (error) => {
        this.saving = false;
        this.applyValidationDetails(error);
        this.changeDetector.detectChanges();
        this.snack.open(this.saveErrorMessage(error), 'Close', { duration: 4000, panelClass: 'snack-error' });
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/seller']);
  }

  private saveErrorMessage(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 400) return 'Check the product fields and try again.';
    if (status === 403) return 'Only sellers can manage products.';
    if (status === 404) return 'This product is no longer available.';
    if (status === 409) return 'This product conflicts with existing data.';
    if (status === 503) return 'Image validation is temporarily unavailable. Try again.';
    if (status === 0) return 'Cannot reach the server. Check your connection.';
    return 'Could not save the product. Try again.';
  }

  private applyValidationDetails(error: unknown): void {
    if (!(error instanceof HttpErrorResponse) || error.status !== 400 || !isRecord(error.error) || !isRecord(error.error['details'])) {
      return;
    }

    for (const [field, message] of Object.entries(error.error['details'])) {
      const control = this.form.get(field);
      if (!control || typeof message !== 'string' || message.trim().length === 0) continue;
      control.setErrors({ ...control.errors, server: message });
      control.markAsTouched();
    }
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
