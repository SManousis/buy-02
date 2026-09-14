import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth';
import { authTokenInterceptor } from './auth-token-interceptor';

describe('authTokenInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  let logoutCalls: number;
  let navigations: string[][];

  beforeEach(() => {
    logoutCalls = 0;
    navigations = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authTokenInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            getToken: () => 'signed-token',
            logout: () => logoutCalls++,
          },
        },
        { provide: Router, useValue: { navigate: (commands: string[]) => navigations.push(commands) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });

  afterEach(() => requests.verify());

  it('attaches the bearer token to protected API requests', () => {
    http.get('http://localhost:8080/products').subscribe();

    const request = requests.expectOne('http://localhost:8080/products');
    expect(request.request.headers.get('Authorization')).toBe('Bearer signed-token');
    request.flush([]);
  });

  it('does not attach a bearer token to public login requests', () => {
    http.post('http://localhost:8080/auth/login', {}).subscribe();

    const request = requests.expectOne('http://localhost:8080/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('does not leak the bearer token to a non-API origin', () => {
    http.get('https://analytics.example/pixel').subscribe();

    const request = requests.expectOne('https://analytics.example/pixel');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('clears the session and redirects to login after an unauthorized API response', () => {
    http.get('http://localhost:8080/products/my').subscribe({ error: () => undefined });

    const request = requests.expectOne('http://localhost:8080/products/my');
    request.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutCalls).toBe(1);
    expect(navigations).toEqual([['/auth/login']]);
  });
});
