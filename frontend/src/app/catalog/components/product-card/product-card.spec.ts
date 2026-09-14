import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CatalogModule } from '../../catalog-module';
import { ProductCard } from './product-card';

describe('ProductCard', () => {
  let component: ProductCard;
  let fixture: ComponentFixture<ProductCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CatalogModule],
      providers: [provideRouter([])],
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
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('uses the shipped placeholder when a product has no images', () => {
    expect(component.imageUrl).toBe('assets/placeholder.svg');
  });
});
