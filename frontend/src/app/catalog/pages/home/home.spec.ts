import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { CatalogModule } from '../../catalog-module';
import { AuthService } from '../../../shared/services/auth';
import { ProductService } from '../../../shared/services/product';
import { Home } from './home';

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CatalogModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser$: of(null) } },
        { provide: ProductService, useValue: { getAll: () => of([]) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
