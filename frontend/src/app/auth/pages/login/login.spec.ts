import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, of, throwError } from 'rxjs';

import { AuthModule } from '../../auth-module';
import { AuthService } from '../../../shared/services/auth';
import { Login } from './login';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let loginResult: () => Observable<{ role: string }>;
  let snackMessages: string[];

  beforeEach(async () => {
    loginResult = () => of({ role: 'CLIENT' });
    snackMessages = [];
    await TestBed.configureTestingModule({
      imports: [AuthModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login: () => loginResult() } },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('identifies invalid email credentials in the login feedback', () => {
    loginResult = () => throwError(() => new HttpErrorResponse({ status: 401 }));
    component.form.setValue({ email: 'seller@example.com', password: 'password123' });

    component.submit();

    expect(snackMessages).toEqual(['Invalid email or password.']);
  });
});
