import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { ProfileModule } from '../../profile-module';
import { AuthService, UpdateProfileRequest } from '../../../shared/services/auth';
import { MediaService } from '../../../shared/services/media';
import { Profile } from './profile';

describe('Profile', () => {
  let component: Profile;
  let fixture: ComponentFixture<Profile>;
  let updateRequests: UpdateProfileRequest[];
  let updateResult: (request: UpdateProfileRequest) => Observable<unknown>;
  let snackMessages: string[];

  beforeEach(async () => {
    updateRequests = [];
    snackMessages = [];
    updateResult = (request) => of({
      id: 'seller-1', username: request.username ?? 'Ariadne', email: 'ariadne@example.com',
      role: 'SELLER', avatarMediaId: request.removeAvatar ? null : request.avatarMediaId ?? 'avatar-1',
      createdAt: '2026-09-03T00:00:00Z',
    });
    await TestBed.configureTestingModule({
      imports: [ProfileModule],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            getProfile: () => of({
              id: 'seller-1', username: 'Ariadne', email: 'ariadne@example.com', role: 'SELLER',
              avatarMediaId: 'avatar-1', createdAt: '2026-09-03T00:00:00Z',
            }),
            updateProfile: (request: UpdateProfileRequest) => {
              updateRequests.push(request);
              return updateResult(request);
            },
          },
        },
        {
          provide: MediaService,
          useValue: {
            upload: () => of({ id: 'avatar-2', url: '/media/images/avatar-2', originalFileName: 'avatar.png', contentType: 'image/png', size: 4 }),
            getImageUrl: (id: string) => `/media/images/${id}`,
          },
        },
        { provide: MatSnackBar, useValue: { open: (message: string) => snackMessages.push(message) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Profile);
    component = fixture.componentInstance;
  });

  it('loads the profile and saves a trimmed username', () => {
    fixture.detectChanges();

    expect(component.profile?.username).toBe('Ariadne');
    expect(component.avatarUrl).toBe('/media/images/avatar-1');

    component.form.setValue({ username: '  Updated seller  ' });
    component.save();

    expect(updateRequests).toEqual([{ username: 'Updated seller' }]);
  });

  it('uploads a seller avatar then persists only its media ID', () => {
    fixture.detectChanges();
    const file = new File(['png'], 'avatar.png', { type: 'image/png' });

    component.onAvatarSelected({ target: { files: [file], value: '' } } as unknown as Event);

    expect(updateRequests).toEqual([{ avatarMediaId: 'avatar-2' }]);
    expect(component.avatarUrl).toBe('/media/images/avatar-2');
  });

  it('removes an existing seller avatar without deleting shared media', () => {
    fixture.detectChanges();

    component.removeAvatar();

    expect(updateRequests).toEqual([{ removeAvatar: true }]);
    expect(component.avatarUrl).toBeNull();
  });

  it('shows backend validation details on their matching profile field', () => {
    fixture.detectChanges();
    component.form.setValue({ username: 'Updated seller' });
    updateResult = () => throwError(() => new HttpErrorResponse({
      status: 400,
      error: { details: { username: 'Username contains unsupported characters' } },
    }));

    component.save();
    fixture.detectChanges();

    expect(component.form.get('username')?.getError('server')).toBe('Username contains unsupported characters');
    expect(fixture.nativeElement.textContent).toContain('Username contains unsupported characters');
  });

  it('keeps generic profile feedback for a 400 without usable details', () => {
    fixture.detectChanges();
    component.form.setValue({ username: 'Updated seller' });
    updateResult = () => throwError(() => new HttpErrorResponse({ status: 400, error: {} }));

    component.save();

    expect(component.form.get('username')?.hasError('server')).toBe(false);
    expect(snackMessages).toContain('Could not update your profile.');
  });
});
