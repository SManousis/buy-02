import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { CatalogModule } from '../../catalog-module';
import { Product, ProductService } from '../../../shared/services/product';
import { ProductList } from './product-list';

describe('ProductList', () => {
  let component: ProductList;
  let fixture: ComponentFixture<ProductList>;
  let getAll: () => Observable<Product[]>;

  beforeEach(async () => {
    getAll = () => of([]);
    await TestBed.configureTestingModule({
      imports: [CatalogModule],
      providers: [
        provideRouter([]),
        { provide: ProductService, useValue: { getAll: () => getAll() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductList);
    component = fixture.componentInstance;
  });

  it('shows a loading state before the catalog request resolves', () => {
    const response = new Subject<Product[]>();
    getAll = () => response;

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Loading products...');
  });

  it('renders the returned products after the catalog request succeeds', () => {
    const response = new Subject<Product[]>();
    getAll = () => response;
    fixture.detectChanges();

    response.next([{
      id: 'olive-oil', name: 'Olive oil', description: 'Cold pressed', price: 12.5,
      imageIds: [], sellerId: 'seller-1',
    }]);
    response.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Olive oil');
    expect(fixture.nativeElement.querySelectorAll('app-product-card').length).toBe(1);
  });

  it('shows an error message and retries when the catalog request fails', () => {
    let calls = 0;
    getAll = () => ++calls === 1 ? throwError(() => new Error('offline')) : of([]);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Products could not be loaded');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(calls).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('No products are available yet.');
  });
});
