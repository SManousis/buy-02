import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  imageIds: string[];
  sellerId: string;
  stock?: number;
}

export interface CreateProductRequest {
  name: string;
  description: string;
  price: number;
  imageIds: string[];
  stock?: number;
}

export interface ProductFilters {
  q?: string;
  minPrice?: number;
  maxPrice?: number;
  sellerId?: string;
  inStock?: boolean;
  sort?: 'newest' | 'price_asc' | 'price_desc';
  page?: number;
  size?: number;
}

export interface SellerSummary {
  id: string;
  username: string;
}

type ProductPayload = Omit<Product, 'imageIds'> & {
  imageIds?: string[];
  imageUrls?: string[];
};

@Injectable({ providedIn: 'root' })
export class ProductService {
  private base = `${environment.apiBaseUrl}/products`;

  constructor(private http: HttpClient) {}

  getAll(filters: ProductFilters = {}): Observable<Product[]> {
    let params = new HttpParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
    });
    return this.http.get<ProductPayload[]>(this.base, { params })
      .pipe(map((products) => products.map((product) => this.normalizeProduct(product))));
  }

  getSellers(): Observable<SellerSummary[]> {
    return this.http.get<SellerSummary[]>(`${environment.apiBaseUrl}/sellers`);
  }

  getById(id: string): Observable<Product> {
    return this.http.get<ProductPayload>(`${this.base}/${id}`)
      .pipe(map((product) => this.normalizeProduct(product)));
  }

  create(data: CreateProductRequest): Observable<Product> {
    return this.http.post<ProductPayload>(this.base, data)
      .pipe(map((product) => this.normalizeProduct(product)));
  }

  update(id: string, data: Partial<CreateProductRequest>): Observable<Product> {
    return this.http.put<ProductPayload>(`${this.base}/${id}`, data)
      .pipe(map((product) => this.normalizeProduct(product)));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  getMyProducts(): Observable<Product[]> {
    return this.http.get<ProductPayload[]>(`${this.base}/my`)
      .pipe(map((products) => products.map((product) => this.normalizeProduct(product))));
  }

  private normalizeProduct(payload: ProductPayload): Product {
    const { imageUrls, ...product } = payload;
    return { ...product, imageIds: product.imageIds ?? imageUrls ?? [] };
  }
}
