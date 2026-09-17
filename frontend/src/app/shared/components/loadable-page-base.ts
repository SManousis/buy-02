import { ChangeDetectorRef, Directive, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subject, takeUntil, timeout } from 'rxjs';

/**
 * Loading behaviour common to pages that fetch one resource on init and offer a
 * retry: track the loading and error flags, time the request out, and cancel it
 * when the page is destroyed. Subclasses supply the request and store the result.
 *
 * Decorated with @Directive() because Angular requires that on a base class
 * that declares lifecycle hooks and constructor injection.
 */
@Directive()
export abstract class LoadablePageBase<T> implements OnInit, OnDestroy {
  loading = true;
  loadError = false;

  protected readonly destroy$ = new Subject<void>();

  protected constructor(protected readonly changeDetector: ChangeDetectorRef) {}

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
    this.fetch()
      .pipe(timeout(10_000), takeUntil(this.destroy$))
      .subscribe({
        next: (value) => {
          this.onLoaded(value);
          this.loading = false;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.loadError = true;
          this.loading = false;
          this.changeDetector.detectChanges();
          this.onLoadError();
        },
      });
  }

  protected abstract fetch(): Observable<T>;

  protected abstract onLoaded(value: T): void;

  protected onLoadError(): void {}
}
