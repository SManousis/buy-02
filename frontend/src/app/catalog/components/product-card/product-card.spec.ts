import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { CatalogModule } from '../../catalog-module';
import { CartService } from '../../../shared/services/cart';
import { AuthService } from '../../../shared/services/auth';
import { ProductCard } from './product-card';

describe('ProductCard', () => {
  let component: ProductCard;
  let fixture: ComponentFixture<ProductCard>;
  let isLoggedIn: boolean;
  let hasRole: boolean;

  beforeEach(async () => {
    isLoggedIn = true;
    hasRole = false;

    await TestBed.configureTestingModule({
      imports: [CatalogModule],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            addItem: () => of({ id: 'cart-1', items: [], subtotal: 0, updatedAt: '2024-01-01T00:00:00Z' }),
          },
        },
        {
          provide: AuthService,
          useValue: { isLoggedIn: () => isLoggedIn, hasRole: (role: string) => hasRole && role === 'SELLER' },
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
});
