import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { Product, ProductService } from './product';

describe('ProductService', () => {
  let service: ProductService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads public products through the API Gateway', () => {
    service.getAll().subscribe();

    const request = http.expectOne('http://localhost:8080/products');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loads seller products through the API Gateway', () => {
    service.getMyProducts().subscribe();

    const request = http.expectOne('http://localhost:8080/products/my');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('normalizes legacy imageUrls responses without dropping associations', () => {
    let product: Product | undefined;
    service.getById('product-1').subscribe((response) => product = response);

    const request = http.expectOne('http://localhost:8080/products/product-1');
    request.flush({
      id: 'product-1',
      sellerId: 'seller-1',
      name: 'Legacy product',
      description: 'Description',
      price: 10,
      stock: 1,
      imageUrls: ['media-legacy'],
    });

    expect(product?.imageIds).toEqual(['media-legacy']);
  });
});
