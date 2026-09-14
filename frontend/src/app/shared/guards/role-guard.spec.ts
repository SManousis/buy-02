import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth';
import { RoleGuard } from './role-guard';

describe('RoleGuard', () => {
  let hasRole: (role: string) => boolean;
  let requestedRedirects: string[][];
  let redirect: UrlTree;

  beforeEach(() => {
    hasRole = () => false;
    requestedRedirects = [];
    redirect = { redirected: true } as unknown as UrlTree;
    TestBed.configureTestingModule({
      providers: [
        RoleGuard,
        { provide: AuthService, useValue: { hasRole: (role: string) => hasRole(role) } },
        {
          provide: Router,
          useValue: { createUrlTree: (commands: string[]) => {
            requestedRedirects.push(commands);
            return redirect;
          } },
        },
      ],
    });
  });

  it('allows a seller to enter seller routes', () => {
    hasRole = (role) => role === 'SELLER';
    const result = TestBed.inject(RoleGuard).canActivate(
      { data: { role: 'SELLER' } } as unknown as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot,
    );

    expect(result).toBe(true);
    expect(requestedRedirects).toEqual([]);
  });

  it('redirects a client away from a seller route', () => {
    const result = TestBed.inject(RoleGuard).canActivate(
      { data: { role: 'SELLER' } } as unknown as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot,
    );

    expect(result).toBe(redirect);
    expect(requestedRedirects).toEqual([['/']]);
  });
});
