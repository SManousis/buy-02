import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { AppModule } from '../../app-module';
import { AuthService } from '../../shared/services/auth';
import { MediaService } from '../../shared/services/media';
import { Header } from './header';

describe('Header', () => {
  let component: Header;
  let fixture: ComponentFixture<Header>;
  let currentUser: BehaviorSubject<{ username: string; role: 'CLIENT' | 'SELLER' } | null>;
  let currentProfile: BehaviorSubject<{ avatarMediaId: string | null } | null>;
  let logoutCalls: number;

  beforeEach(async () => {
    currentUser = new BehaviorSubject<{ username: string; role: 'CLIENT' | 'SELLER' } | null>(null);
    currentProfile = new BehaviorSubject<{ avatarMediaId: string | null } | null>(null);
    logoutCalls = 0;
    await TestBed.configureTestingModule({
      imports: [AppModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser$: currentUser, currentProfile$: currentProfile, logout: () => logoutCalls++ } },
        { provide: MediaService, useValue: { getImageUrl: (id: string) => `/media/images/${id}` } },
      ],
    }).compileComponents();

    // AppModule brings in AppRoutingModule, whose '' route is a lazy
    // `loadChildren: () => import('./catalog/catalog-module')`, and the root
    // router starts that import during its initial navigation. Drain it here:
    // left pending, it can resolve after Vitest has torn this environment down
    // and surface as `EnvironmentTeardownError: Cannot load '/chunk-*.js' ...`
    // against whichever spec files happened to be loading at that moment.
    // Awaited before the fixture exists, so the first detectChanges below is
    // still this component's initial render.
    await TestBed.inject(Router).navigate(['/']);

    fixture = TestBed.createComponent(Header);
    component = fixture.componentInstance;
  });

  it('renders the signed-in seller avatar and dashboard entry from the profile stream', () => {
    currentUser.next({ username: 'Ariadne', role: 'SELLER' });
    fixture.detectChanges(false);

    expect(fixture.nativeElement.querySelector('.avatar-btn')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.seller-btn')?.textContent).toContain('Dashboard');
    expect(component.username).toBe('Ariadne');
  });

  it('renders the current avatar from its media endpoint without using browser storage', () => {
    currentUser.next({ username: 'Ariadne', role: 'SELLER' });
    currentProfile.next({ avatarMediaId: 'avatar-1' });
    fixture.detectChanges(false);

    expect(fixture.nativeElement.querySelector('.header-avatar')?.getAttribute('src')).toBe('/media/images/avatar-1');
  });

  it('clears the session when the user signs out', () => {
    fixture.detectChanges();

    component.logout();

    expect(logoutCalls).toBe(1);
  });
});
