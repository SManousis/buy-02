import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { EMPTY, Observable, of, throwError } from 'rxjs';

import { CatalogModule } from '../../catalog-module';
import { Cart, CartService } from '../../../shared/services/cart';
import { AuthService } from '../../../shared/services/auth';
import { ProductCard } from './product-card';

const CART: Cart = { id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' };

describe('ProductCard', () => {
  let component: ProductCard;
  let fixture: ComponentFixture<ProductCard>;
  let isLoggedIn: boolean;
  let hasRole: boolean;
  let addItem: (productId: string, quantity: number) => Observable<Cart>;
  let added: { productId: string; quantity: number } | null;
  let snackMessages: string[];

  beforeEach(async () => {
    isLoggedIn = true;
    hasRole = false;
    added = null;
    snackMessages = [];
    addItem = (productId, quantity) => {
      added = { productId, quantity };
      return of(CART);
    };

    await TestBed.configureTestingModule({
      imports: [CatalogModule],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: { addItem: (id: string, qty: number) => addItem(id, qty) },
        },
        {
          provide: AuthService,
          useValue: { isLoggedIn: () => isLoggedIn, hasRole: (role: string) => hasRole && role === 'SELLER' },
        },
        {
          provide: MatSnackBar,
          useValue: {
            open: (message: string) => {
              snackMessages.push(message);
              return { onAction: () => EMPTY };
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductCard);
    component = fixture.componentInstance;
    component.product = {
      id: 'product-1',
      name: 'Olive oil',
      description: 'Greek extra virgin olive oil',
      price: 12.5,
      imageIds: [],
      sellerId: 'seller-1',
    };
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('uses the shipped placeholder when a product has no images', () => {
    expect(component.imageUrl).toBe('assets/placeholder.svg');
  });

  it('builds a media URL from the first image when one exists', () => {
    component.product = { ...component.product, imageIds: ['image-1', 'image-2'] };

    expect(component.imageUrl).toContain('/media/images/image-1');
  });

  it('offers add-to-cart to a signed-in buyer', () => {
    fixture.detectChanges();

    expect(component.canAddToCart).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Add to cart');
  });

  it('hides add-to-cart from a signed-out visitor', () => {
    isLoggedIn = false;
    fixture.detectChanges();

    expect(component.canAddToCart).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Add to cart');
  });

  it('hides add-to-cart from a seller', () => {
    hasRole = true;
    fixture.detectChanges();

    expect(component.canAddToCart).toBe(false);
  });

  it('hides add-to-cart when the product is out of stock', () => {
    component.product = { ...component.product, stock: 0 };
    fixture.detectChanges();

    expect(component.canAddToCart).toBe(false);
  });

  it('adds a single unit and confirms it, without navigating the card', () => {
    const event = new Event('click');
    const stopPropagation = vi.spyOn(event, 'stopPropagation');
    fixture.detectChanges();

    component.addToCart(event);

    expect(stopPropagation).toHaveBeenCalled();
    expect(added).toEqual({ productId: 'product-1', quantity: 1 });
    expect(component.addingToCart).toBe(false);
    expect(snackMessages).toEqual(['Olive oil added to cart']);
  });

  it('reports the server message when stock is short', () => {
    addItem = () => throwError(() => new HttpErrorResponse({
      status: 409,
      error: { message: 'Insufficient stock for product: Olive oil' },
    }));
    fixture.detectChanges();

    component.addToCart(new Event('click'));

    expect(snackMessages).toEqual(['Insufficient stock for product: Olive oil']);
    expect(component.addingToCart).toBe(false);
  });

  it('reports a connection failure distinctly', () => {
    addItem = () => throwError(() => new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();

    component.addToCart(new Event('click'));

    expect(snackMessages).toEqual(['Cannot reach the server. Check your connection.']);
  });

  it('reports a removed product distinctly', () => {
    addItem = () => throwError(() => new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();

    component.addToCart(new Event('click'));

    expect(snackMessages).toEqual(['This product is no longer available.']);
  });

  it('ignores the click when the visitor is not allowed to add to cart', () => {
    isLoggedIn = false;
    fixture.detectChanges();

    component.addToCart(new Event('click'));

    expect(added).toBeNull();
    expect(snackMessages).toEqual([]);
  });
});
