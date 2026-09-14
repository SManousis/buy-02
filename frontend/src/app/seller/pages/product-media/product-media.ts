import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { catchError, concatMap, defer, forkJoin, Observable, of, Subject, takeUntil, tap } from 'rxjs';
import { ProductService, Product } from '../../../shared/services/product';
import { MediaService, MediaUploadResponse } from '../../../shared/services/media';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog';

interface ProductImage {
  id: string;
  url: string;
  name: string;
}

@Component({
  selector: 'app-product-media',
  standalone: false,
  templateUrl: './product-media.html',
  styleUrl: './product-media.scss',
})
export class ProductMedia implements OnInit, OnDestroy {
  product: Product | null = null;
  images: ProductImage[] = [];
  loading = true;
  notFound = false;
  uploading = false;
  readonly maxImageSize = 2 * 1024 * 1024;
  readonly allowedImageTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);

  private destroy$ = new Subject<void>();
  private mediaMutations$ = new Subject<Observable<unknown>>();
  private productId!: string;
  private destroyed = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private productService: ProductService,
    private mediaService: MediaService,
    private snack: MatSnackBar,
    private dialog: MatDialog,
    private changeDetector: ChangeDetectorRef,
  ) {
    this.mediaMutations$.pipe(concatMap((mutation) => mutation)).subscribe();
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.loading = false;
      this.notFound = true;
      return;
    }
    this.productId = id;

    this.productService.getById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (product) => {
          this.product = product;
          this.images = (product.imageIds ?? []).map((imgId) => ({
            id: imgId,
            url: this.mediaService.getImageUrl(imgId),
            name: 'Product image',
          }));
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.notFound = true;
          this.changeDetector.detectChanges();
        },
      });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.destroy$.next();
    this.destroy$.complete();
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';

    const validFiles = files.filter((file) => this.validateImage(file));
    if (validFiles.length === 0) return;

    this.uploading = true;
    forkJoin(validFiles.map((file) => this.mediaService.upload(file).pipe(
      catchError(() => {
        this.runViewEffect(() => {
          this.snack.open(`Could not upload ${file.name}.`, 'Close', {
            duration: 4000, panelClass: 'snack-error',
          });
        });
        return of(null);
      }),
    )))
      .subscribe((responses) => {
        const uploaded = responses.filter((response): response is MediaUploadResponse => response !== null);
        const uploadedIds = uploaded.map((response) => response.id);
        if (uploadedIds.length === 0) {
          this.uploading = false;
          this.runViewEffect(() => {
            this.changeDetector.detectChanges();
          });
          return;
        }

        this.queueMediaMutation(() => {
          const nextImageIds = [...this.images.map((image) => image.id), ...uploadedIds];
          this.images.push(...uploaded.map((response) => ({
            id: response.id,
            url: this.mediaService.getImageUrl(response.id),
            name: response.originalFileName,
          })));
          this.runViewEffect(() => {
            this.changeDetector.detectChanges();
          });
          return this.persistImages(nextImageIds, uploadedIds);
        });
      });
  }

  removeImage(image: ProductImage): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { message: 'Permanently delete this image? This cannot be undone.' },
      width: '360px',
    });
    ref.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.queueMediaMutation(() => this.mediaService.delete(image.id).pipe(
          concatMap(() => {
            this.images = this.images.filter((candidate) => candidate.id !== image.id);
            const remainingIds = this.images.map((candidate) => candidate.id);
            this.runViewEffect(() => this.changeDetector.detectChanges());
            return this.persistImages(remainingIds);
          }),
          catchError(() => {
              this.runViewEffect(() => {
                this.snack.open('Could not delete image. Try again.', 'Close', {
                  duration: 4000, panelClass: 'snack-error',
                });
                this.changeDetector.detectChanges();
              });
              return of(void 0);
          }),
        ));
      });
  }

  back(): void {
    this.router.navigate(['/seller']);
  }

  private persistImages(imageIds: string[], uploadedIds: string[] = []): Observable<unknown> {
    return this.productService.update(this.productId, { imageIds })
      .pipe(
        tap(() => {
          if (uploadedIds.length === 0) return;
          this.uploading = false;
          this.runViewEffect(() => {
            this.changeDetector.detectChanges();
          });
        }),
        catchError(() => {
          uploadedIds.forEach((id) => this.mediaService.delete(id)
            .subscribe({
              error: () => this.runViewEffect(() => {
                this.snack.open('Could not clean up an unattached image.', 'Close', {
                  duration: 4000, panelClass: 'snack-error',
                });
              }),
            }));
          this.images = this.images.filter((image) => !uploadedIds.includes(image.id));
          this.uploading = false;
          this.runViewEffect(() => {
            this.snack.open('Could not save image changes to the product.', 'Close', {
              duration: 4000, panelClass: 'snack-error',
            });
            this.changeDetector.detectChanges();
          });
          return of(void 0);
        }),
      );
  }

  private queueMediaMutation(mutation: () => Observable<unknown>): void {
    this.mediaMutations$.next(defer(mutation));
  }

  private runViewEffect(effect: () => void): void {
    if (!this.destroyed) effect();
  }

  private validateImage(file: File): boolean {
    if (!this.allowedImageTypes.has(file.type)) {
      this.snack.open('Only JPEG, PNG, and WEBP images are allowed.', 'Close', {
        duration: 4000, panelClass: 'snack-error',
      });
      return false;
    }
    if (file.size > this.maxImageSize) {
      this.snack.open('Images must be 2 MB or smaller.', 'Close', {
        duration: 4000, panelClass: 'snack-error',
      });
      return false;
    }
    return true;
  }
}
