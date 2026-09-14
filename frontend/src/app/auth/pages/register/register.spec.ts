import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';

import { AuthModule } from '../../auth-module';
import { AuthService } from '../../../shared/services/auth';
import { Register } from './register';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { register: () => of({}) } },
        { provide: MatSnackBar, useValue: { open: () => undefined } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('matches the backend username limits and rejects whitespace-only names', () => {
    component.form.patchValue({ username: 'U'.repeat(50) });
    expect(component.form.get('username')?.valid).toBe(true);

    component.form.patchValue({ username: ' '.repeat(3) });
    expect(component.form.get('username')?.valid).toBe(false);

    component.form.patchValue({ username: 'U'.repeat(51) });
    expect(component.form.get('username')?.hasError('maxlength')).toBe(true);
  });
});
