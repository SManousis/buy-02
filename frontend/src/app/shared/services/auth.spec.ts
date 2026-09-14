import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from './auth';
import { environment as productionEnvironment } from '../../../environments/environment.prod';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the same-origin Nginx Gateway prefix in production', () => {
    expect(productionEnvironment.apiBaseUrl).toBe('/api');
  });

  it('sends login through the API Gateway', () => {
    service.login('seller@example.com', 'password123').subscribe();

    const request = http.expectOne('http://localhost:8080/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'seller@example.com',
      password: 'password123',
    });
    request.flush({ token: 'token', userId: 'seller-1', username: 'seller', role: 'SELLER' });
  });

  it('sends registration through the API Gateway', () => {
    service.register('client', 'password123', 'client@example.com', 'CLIENT').subscribe();

    const request = http.expectOne('http://localhost:8080/auth/register');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      username: 'client',
      password: 'password123',
      email: 'client@example.com',
      role: 'CLIENT',
    });
    request.flush({ token: 'token', userId: 'client-1', username: 'client', role: 'CLIENT' });
  });

  it('loads and updates the authenticated profile without storing profile data', () => {
    const loadedProfiles: unknown[] = [];
    const updatedProfiles: unknown[] = [];

    service.getProfile().subscribe((profile) => loadedProfiles.push(profile));
    const getRequest = http.expectOne('http://localhost:8080/me');
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({ id: 'seller-1', username: 'seller', role: 'SELLER', avatarMediaId: 'avatar-1' });

    service.updateProfile({ username: 'updated', avatarMediaId: 'avatar-2' })
      .subscribe((profile) => updatedProfiles.push(profile));
    const putRequest = http.expectOne('http://localhost:8080/me');
    expect(putRequest.request.method).toBe('PUT');
    expect(putRequest.request.body).toEqual({ username: 'updated', avatarMediaId: 'avatar-2' });
    putRequest.flush({ id: 'seller-1', username: 'updated', role: 'SELLER', avatarMediaId: 'avatar-2' });

    expect(loadedProfiles).toEqual([{ id: 'seller-1', username: 'seller', role: 'SELLER', avatarMediaId: 'avatar-1' }]);
    expect(updatedProfiles).toEqual([{ id: 'seller-1', username: 'updated', role: 'SELLER', avatarMediaId: 'avatar-2' }]);
  });
});
