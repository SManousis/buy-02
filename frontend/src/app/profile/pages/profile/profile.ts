import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, concatMap, takeUntil, timeout } from 'rxjs';

import { AuthService, UserProfile } from '../../../shared/services/auth';
import { MediaService } from '../../../shared/services/media';

function notBlank(control: { value: unknown }): { whitespace: true } | null {
  return typeof control.value === 'string' && control.value.trim().length === 0 ? { whitespace: true } : null;
}

@Component({
  selector: 'app-profile',
  standalone: false,
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit, OnDestroy {
  readonly maxImageSize = 2 * 1024 * 1024;
  readonly allowedImageTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);
  form: FormGroup;
  profile: UserProfile | null = null;
  loading = true;
  saving = false;
  uploading = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly media: MediaService,
    private readonly snack: MatSnackBar,
    private readonly changeDetector: ChangeDetectorRef,
  ) {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50), notBlank]],
    });
  }

  get isSeller(): boolean {
    return this.profile?.role === 'SELLER';
  }

  get avatarUrl(): string | null {
    return this.profile?.avatarMediaId ? this.media.getImageUrl(this.profile.avatarMediaId) : null;
  }

  ngOnInit(): void {
    this.auth.getProfile()
      .pipe(
        timeout(10_000),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (profile) => {
          this.profile = profile;
          this.form.patchValue({ username: profile.username });
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: (error) => {
          this.loading = false;
          this.changeDetector.detectChanges();
          this.showError(error, 'Could not load your profile.');
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  save(): void {
    if (this.form.invalid || this.saving || !this.profile) return;
    this.saving = true;
    const username = (this.form.value.username as string).trim();
    this.auth.updateProfile({ username })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (profile) => {
          this.profile = profile;
          this.form.patchValue({ username: profile.username });
          this.saving = false;
          this.snack.open('Profile updated.', 'Close', { duration: 3000, panelClass: 'snack-success' });
        },
        error: (error) => {
          this.saving = false;
          this.applyValidationDetails(error);
          this.showError(error, 'Could not update your profile.');
        },
      });
  }

  onAvatarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file || !this.isSeller || this.uploading) return;
    if (!this.validateImage(file)) return;

    this.uploading = true;
    this.media.upload(file)
      .pipe(
        concatMap((uploaded) => this.auth.updateProfile({ avatarMediaId: uploaded.id })),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (profile) => {
          this.profile = profile;
          this.uploading = false;
          this.snack.open('Avatar updated.', 'Close', { duration: 3000, panelClass: 'snack-success' });
        },
        error: (error) => {
          this.uploading = false;
          this.showError(error, 'Could not update your avatar.');
        },
      });
  }

  removeAvatar(): void {
    if (!this.isSeller || !this.profile?.avatarMediaId || this.saving) return;
    this.saving = true;
    this.auth.updateProfile({ removeAvatar: true })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (profile) => {
          this.profile = profile;
          this.saving = false;
          this.snack.open('Avatar removed.', 'Close', { duration: 3000, panelClass: 'snack-success' });
        },
        error: (error) => {
          this.saving = false;
          this.showError(error, 'Could not remove your avatar.');
        },
      });
  }

  private validateImage(file: File): boolean {
    if (!this.allowedImageTypes.has(file.type)) {
      this.snack.open('Only JPEG, PNG, and WEBP images are allowed.', 'Close', { duration: 4000, panelClass: 'snack-error' });
      return false;
    }
    if (file.size > this.maxImageSize) {
      this.snack.open('Images must be 2 MB or smaller.', 'Close', { duration: 4000, panelClass: 'snack-error' });
      return false;
    }
    return true;
  }

  private showError(error: unknown, fallback: string): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const message = status === 409 ? 'That username is already in use.'
      : status === 403 ? 'You are not allowed to perform that action.'
      : status === 0 ? 'Cannot reach the server. Check your connection.'
      : fallback;
    this.snack.open(message, 'Close', { duration: 4000, panelClass: 'snack-error' });
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
