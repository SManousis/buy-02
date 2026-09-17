import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { CatalogModule } from '../../catalog-module';
import { Product, ProductService } from '../../../shared/services/product';
import { Cart, CartService } from '../../../shared/services/cart';
import { AuthService } from '../../../shared/services/auth';
import { ProductDetail } from './product-detail';

const PRODUCT: Product = {
  id: 'product-1', name: 'Olive oil', description: 'Cold pressed', price: 12.5,
  imageIds: [], sellerId: 'seller-1', stock: 5,
};

describe('ProductDetail', () => {
  let component: ProductDetail;
  let fixture: ComponentFixture<ProductDetail>;
  let isLoggedIn: boolean;
  let hasRole: boolean;
  let addItem: (productId: string, quantity: number) => Observable<Cart>;
  let snackMessages: string[];

  beforeEach(async () => {
    isLoggedIn = true;
    hasRole = false;
    addItem = () => of({ id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' });
    snackMessages = [];

    await TestBed.configureTestingModule({
      imports: [CatalogModule],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'product-1' } } } },
        { provide: ProductService, useValue: { getById: () => of(PRODUCT) } },
        { provide: CartService, useValue: { addItem: (id: string, qty: number) => addItem(id, qty) } },
        { provide: AuthService, useValue: { isLoggedIn: () => isLoggedIn, hasRole: (role: string) => hasRole && role === 'SELLER' } },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductDetail);
    component = fixture.componentInstance;
  });

  it('shows the add-to-cart control for a signed-in buyer with stock available', () => {
    fixture.detectChanges();

    expect(component.canAddToCart).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Add to cart');
  });

  it('hides the add-to-cart control for a signed-out visitor', () => {
    isLoggedIn = false;
    fixture.detectChanges();

    expect(component.canAddToCart).toBe(false);
  });

  it('hides the add-to-cart control for a seller', () => {
    hasRole = true;
    fixture.detectChanges();

    expect(component.canAddToCart).toBe(false);
  });

  it('caps the quantity selector at the available stock', () => {
    fixture.detectChanges();
    for (let i = 0; i < 10; i++) component.incrementQuantity();

    expect(component.quantity).toBe(5);
  });

  it('adds the selected quantity to the cart and confirms success', () => {
    let received: { id: string; qty: number } | null = null;
    addItem = (id, qty) => { received = { id, qty }; return of({ id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' }); };
    fixture.detectChanges();

    component.incrementQuantity();
    component.addToCart();

    expect(received).toEqual({ id: 'product-1', qty: 2 });
    expect(snackMessages).toEqual(['Added to cart!']);
  });

  it('shows a stock-conflict message when the cart request returns 409', () => {
    addItem = () => throwError(() => new HttpErrorResponse({
      status: 409,
      error: { message: 'Insufficient stock for product: Olive oil' },
    }));
    fixture.detectChanges();

    component.addToCart();

    expect(snackMessages).toEqual(['Insufficient stock for product: Olive oil']);
    expect(component.addingToCart).toBe(false);
  });
});
