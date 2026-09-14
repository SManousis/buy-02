import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { SellerModule } from '../../seller-module';
import { ProductService } from '../../../shared/services/product';
import { ProductForm } from './product-form';

describe('ProductForm', () => {
  let component: ProductForm;
  let fixture: ComponentFixture<ProductForm>;
  let productId: string | null;
  let createPayload: unknown;
  let updatePayload: unknown;
  let getById: () => Observable<unknown>;
  let createResult: () => Observable<unknown>;
  let navigations: string[][];
  let snackMessages: string[];

  beforeEach(async () => {
    productId = null;
    createPayload = undefined;
    updatePayload = undefined;
    getById = () => of({});
    createResult = () => of({});
    navigations = [];
    snackMessages = [];
    await TestBed.configureTestingModule({
      imports: [SellerModule],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => productId } } } },
        {
          provide: ProductService,
          useValue: {
            getById: () => getById(),
            create: (payload: unknown) => {
              createPayload = payload;
              return createResult();
            },
            update: (_id: string, payload: unknown) => {
              updatePayload = payload;
              return of({});
            },
          },
        },
        { provide: Router, useValue: { navigate: (commands: string[]) => navigations.push(commands) } },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductForm);
    component = fixture.componentInstance;
  });

  it('creates a product with the completed form values', () => {
    fixture.detectChanges();
    component.form.setValue({
      name: 'Olive oil', description: 'Cold pressed', price: 12.5,
      stock: 4, imageIds: ['media-1'],
    });

    component.submit();

    expect(createPayload).toEqual({
      name: 'Olive oil', description: 'Cold pressed', price: 12.5,
      stock: 4, imageIds: ['media-1'],
    });
    expect(snackMessages).toEqual(['Product created!']);
    expect(navigations).toEqual([['/seller']]);
  });

  it('loads an existing product and saves edits through the update workflow', () => {
    productId = 'product-1';
    getById = () => of({
      id: 'product-1', name: 'Original name', description: 'Original description', price: 10,
      stock: 1, imageIds: ['media-1'], sellerId: 'seller-1',
    });
    fixture.detectChanges();
    component.form.patchValue({ name: 'Updated name' });

    component.submit();

    expect(updatePayload).toEqual({
      name: 'Updated name', description: 'Original description', price: 10,
      stock: 1, imageIds: ['media-1'],
    });
    expect(snackMessages).toEqual(['Product updated!']);
    expect(navigations).toEqual([['/seller']]);
  });

  it('uses backend length limits and rejects whitespace-only required values', () => {
    fixture.detectChanges();
    component.form.setValue({
      name: ' '.repeat(120), description: ' '.repeat(2000), price: 1, stock: 0, imageIds: [],
    });

    expect(component.form.valid).toBe(false);

    component.form.patchValue({ name: 'N'.repeat(121), description: 'D'.repeat(2001) });

    expect(component.form.get('name')?.hasError('maxlength')).toBe(true);
    expect(component.form.get('description')?.hasError('maxlength')).toBe(true);
  });

  it('does not submit a second request while saving', () => {
    fixture.detectChanges();
    component.form.setValue({ name: 'Olive oil', description: 'Cold pressed', price: 12.5, stock: 4, imageIds: [] });
    component.saving = true;

    component.submit();

    expect(createPayload).toBeUndefined();
  });

  it('explains forbidden, invalid, and unavailable product saves', () => {
    fixture.detectChanges();
    component.form.setValue({ name: 'Olive oil', description: 'Cold pressed', price: 12.5, stock: 4, imageIds: [] });
    createResult = () => throwError(() => new HttpErrorResponse({ status: 403 }));

    component.submit();

    expect(snackMessages).toContain('Only sellers can manage products.');
  });

  it('shows backend validation details on their matching product fields', () => {
    fixture.detectChanges();
    component.form.setValue({ name: 'Olive oil', description: 'Cold pressed', price: 12.5, stock: 4, imageIds: [] });
    createResult = () => throwError(() => new HttpErrorResponse({
      status: 400,
      error: { details: { name: 'Name is already reserved', price: 'Price is outside the allowed range' } },
    }));

    component.submit();
    fixture.detectChanges();

    expect(component.form.get('name')?.getError('server')).toBe('Name is already reserved');
    expect(component.form.get('price')?.getError('server')).toBe('Price is outside the allowed range');
    expect(fixture.nativeElement.textContent).toContain('Name is already reserved');
    expect(fixture.nativeElement.textContent).toContain('Price is outside the allowed range');
  });

  it('keeps the generic product feedback when a 400 has no usable details', () => {
    fixture.detectChanges();
    component.form.setValue({ name: 'Olive oil', description: 'Cold pressed', price: 12.5, stock: 4, imageIds: [] });
    createResult = () => throwError(() => new HttpErrorResponse({ status: 400, error: { details: ['not a field map'] } }));

    component.submit();

    expect(component.form.get('name')?.hasError('server')).toBe(false);
    expect(snackMessages).toContain('Check the product fields and try again.');
  });
});
