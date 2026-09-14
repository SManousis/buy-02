import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, of, Subject, throwError } from 'rxjs';

import { SellerModule } from '../../seller-module';
import { MediaService, MediaUploadResponse } from '../../../shared/services/media';
import { ProductService } from '../../../shared/services/product';
import { ProductMedia } from './product-media';

describe('ProductMedia', () => {
  let component: ProductMedia;
  let fixture: ComponentFixture<ProductMedia>;
  let updateCalls: unknown[];
  let deletedIds: string[];
  let associationFails: boolean;
  let removalConfirmed: boolean;
  let uploadOverride: ((file: File) => Observable<MediaUploadResponse>) | undefined;
  let deleteOverride: ((id: string) => Observable<void>) | undefined;
  let updateOverride: (() => Observable<unknown>) | undefined;
  let snackMessages: string[];
  let dialogMessages: string[];

  beforeEach(async () => {
    updateCalls = [];
    deletedIds = [];
    associationFails = true;
    removalConfirmed = false;
    uploadOverride = undefined;
    deleteOverride = undefined;
    updateOverride = undefined;
    snackMessages = [];
    dialogMessages = [];

    await TestBed.configureTestingModule({
      imports: [SellerModule],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
        {
          provide: ProductService,
          useValue: {
            update: (_id: string, payload: unknown) => {
              updateCalls.push(payload);
              if (updateOverride) return updateOverride();
              return associationFails
                ? throwError(() => new Error('association failed'))
                : of(void 0);
            },
          },
        },
        {
          provide: MediaService,
          useValue: {
            upload: (file: File) => uploadOverride
              ? uploadOverride(file)
              : of({
                id: `media-${file.name}`,
                url: `http://media/${file.name}`,
                originalFileName: file.name,
                contentType: file.type,
                size: file.size,
              }),
            getImageUrl: (id: string) => `http://media/${id}`,
            delete: (id: string) => {
              deletedIds.push(id);
              if (deleteOverride) return deleteOverride(id);
              return of(void 0);
            },
          },
        },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
        {
          provide: MatDialog,
          useValue: {
            open: (_component: unknown, config: { data: { message: string } }) => {
              dialogMessages.push(config.data.message);
              return { afterClosed: () => of(removalConfirmed) };
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductMedia);
    component = fixture.componentInstance;
    (component as unknown as { productId: string }).productId = 'product-1';
  });

  it('batches uploads into one failed association update and compensates every uploaded image', () => {
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      value: [
        new File(['one'], 'one.png', { type: 'image/png' }),
        new File(['two'], 'two.png', { type: 'image/png' }),
      ],
    });

    component.onFilesSelected({ target: input } as unknown as Event);

    expect(updateCalls).toEqual([{ imageIds: ['media-one.png', 'media-two.png'] }]);
    expect(deletedIds).toEqual(['media-one.png', 'media-two.png']);
    expect(component.images).toEqual([]);
  });

  it('persists the remaining image ids after removing an existing image without compensating it', () => {
    associationFails = false;
    removalConfirmed = true;
    component.images = [
      { id: 'media-remove', url: 'http://media/media-remove', name: 'Remove me' },
      { id: 'media-keep', url: 'http://media/media-keep', name: 'Keep me' },
    ];

    component.removeImage(component.images[0]);

    expect(deletedIds).toEqual(['media-remove']);
    expect(updateCalls).toEqual([{ imageIds: ['media-keep'] }]);
    expect(component.images).toEqual([
      { id: 'media-keep', url: 'http://media/media-keep', name: 'Keep me' },
    ]);
  });

  it('serializes a deletion ahead of an upload that finishes while deletion is pending', () => {
    associationFails = false;
    removalConfirmed = true;
    const upload$ = new Subject<MediaUploadResponse>();
    const delete$ = new Subject<void>();
    uploadOverride = () => upload$;
    deleteOverride = () => delete$;
    component.images = [
      { id: 'media-remove', url: 'http://media/media-remove', name: 'Remove me' },
    ];
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      value: [new File(['new'], 'new.png', { type: 'image/png' })],
    });

    component.onFilesSelected({ target: input } as unknown as Event);
    component.removeImage(component.images[0]);
    upload$.next({
      id: 'media-new',
      url: 'http://media/media-new',
      originalFileName: 'new.png',
      contentType: 'image/png',
      size: 3,
    });
    upload$.complete();
    delete$.next();
    delete$.complete();

    expect(updateCalls).toEqual([
      { imageIds: [] },
      { imageIds: ['media-new'] },
    ]);
    expect(component.images).toEqual([
      { id: 'media-new', url: 'http://media/media-new', name: 'new.png' },
    ]);
  });

  it('finishes delayed upload attachment and compensation after component destruction', () => {
    const upload$ = new Subject<MediaUploadResponse>();
    const update$ = new Subject<unknown>();
    uploadOverride = () => upload$;
    updateOverride = () => update$;
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      value: [new File(['one'], 'one.png', { type: 'image/png' })],
    });

    component.onFilesSelected({ target: input } as unknown as Event);
    component.ngOnDestroy();
    upload$.next({
      id: 'media-delayed',
      url: 'http://media/media-delayed',
      originalFileName: 'one.png',
      contentType: 'image/png',
      size: 3,
    });
    upload$.complete();

    expect(updateCalls).toEqual([{ imageIds: ['media-delayed'] }]);
    update$.error(new Error('association failed'));
    expect(deletedIds).toEqual(['media-delayed']);
    expect(snackMessages).toEqual([]);
  });

  it('does not persist a failed attachment when a later queued upload finishes after destruction', () => {
    const firstAssociation$ = new Subject<unknown>();
    const secondUpload$ = new Subject<MediaUploadResponse>();
    let associationAttempt = 0;
    uploadOverride = (file) => file.name === 'second.png'
      ? secondUpload$
      : of({
        id: 'media-first',
        url: 'http://media/media-first',
        originalFileName: file.name,
        contentType: file.type,
        size: file.size,
      });
    updateOverride = () => ++associationAttempt === 1 ? firstAssociation$ : of(void 0);
    const firstInput = document.createElement('input');
    const secondInput = document.createElement('input');
    Object.defineProperty(firstInput, 'files', {
      value: [new File(['first'], 'first.png', { type: 'image/png' })],
    });
    Object.defineProperty(secondInput, 'files', {
      value: [new File(['second'], 'second.png', { type: 'image/png' })],
    });

    component.onFilesSelected({ target: firstInput } as unknown as Event);
    component.onFilesSelected({ target: secondInput } as unknown as Event);
    component.ngOnDestroy();
    firstAssociation$.error(new Error('association failed'));
    secondUpload$.next({
      id: 'media-second',
      url: 'http://media/media-second',
      originalFileName: 'second.png',
      contentType: 'image/png',
      size: 6,
    });
    secondUpload$.complete();

    expect(updateCalls).toEqual([
      { imageIds: ['media-first'] },
      { imageIds: ['media-second'] },
    ]);
  });

  it('describes direct removal as permanent deletion before asking for confirmation', () => {
    component.images = [
      { id: 'media-1', url: 'http://media/media-1', name: 'Delete me' },
    ];

    component.removeImage(component.images[0]);

    expect(dialogMessages).toEqual([
      'Permanently delete this image? This cannot be undone.',
    ]);
  });

  it('rejects an unsupported file before it reaches the upload service', () => {
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      value: [new File(['text'], 'notes.txt', { type: 'text/plain' })],
    });

    component.onFilesSelected({ target: input } as unknown as Event);

    expect(updateCalls).toEqual([]);
    expect(deletedIds).toEqual([]);
    expect(component.images).toEqual([]);
    expect(snackMessages).toEqual(['Only JPEG, PNG, and WEBP images are allowed.']);
  });
});
