import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subject, of } from 'rxjs';

import { SellerModule } from '../../seller-module';
import { ProductService } from '../../../shared/services/product';
import { Product } from '../../../shared/services/product';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let getMyProducts: () => Observable<Product[]>;
  let deleteProduct: (id: string) => Observable<void>;
  let confirmed: boolean;
  let deletedIds: string[];
  let snackMessages: string[];

  beforeEach(async () => {
    getMyProducts = () => of([]);
    deleteProduct = () => of(void 0);
    confirmed = false;
    deletedIds = [];
    snackMessages = [];
    await TestBed.configureTestingModule({
      imports: [SellerModule],
      providers: [
        provideRouter([]),
        {
          provide: ProductService,
          useValue: {
            getMyProducts: () => getMyProducts(),
            delete: (id: string) => {
              deletedIds.push(id);
              return deleteProduct(id);
            },
          },
        },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(confirmed) }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
  });

  it('renders a seller product after the owned-products request succeeds', () => {
    const response = new Subject<Product[]>();
    getMyProducts = () => response;
    fixture.detectChanges();

    response.next([{
      id: 'product-1', name: 'Olive oil', description: 'Cold pressed', price: 12.5,
      imageIds: [], sellerId: 'seller-1', stock: 4,
    }]);
    response.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Olive oil');
    expect(fixture.nativeElement.textContent).toContain('4 in stock');
  });

  it('deletes a confirmed product and refreshes the owned-products list', () => {
    let loads = 0;
    confirmed = true;
    getMyProducts = () => {
      loads++;
      return of([]);
    };
    fixture.detectChanges();
    const product: Product = {
      id: 'product-1', name: 'Olive oil', description: 'Cold pressed', price: 12.5,
      imageIds: [], sellerId: 'seller-1',
    };

    component.deleteProduct(product);

    expect(deletedIds).toEqual(['product-1']);
    expect(loads).toBe(2);
    expect(snackMessages).toEqual(['Product deleted.']);
  });
});
