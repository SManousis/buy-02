import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AuthUser { username: string; role: 'CLIENT' | 'SELLER'; }
export interface AuthResponse { token: string; username: string; role: string; }
export interface UserProfile {
  id: string;
  username: string;
  email: string;
  role: 'CLIENT' | 'SELLER';
  avatarMediaId: string | null;
  createdAt: string;
}
export interface UpdateProfileRequest {
  username?: string;
  avatarMediaId?: string;
  removeAvatar?: boolean;
}

function jwtExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp ? payload.exp * 1000 < Date.now() : false;
  } catch { return true; }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'agora_token';
  private readonly USER_KEY  = 'agora_user';
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private userSubject = new BehaviorSubject<AuthUser | null>(this.loadUser());
  private profileSubject = new BehaviorSubject<UserProfile | null>(null);
  readonly currentUser$ = this.userSubject.asObservable();
  readonly currentProfile$ = this.profileSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      environment.apiBaseUrl + '/auth/login',
      { email, password }
    ).pipe(tap(res => this.saveSession(res)));
  }

  register(username: string, password: string, email: string, role: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      environment.apiBaseUrl + '/auth/register',
      { username, password, email, role }
    ).pipe(tap(res => this.saveSession(res)));
  }

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${environment.apiBaseUrl}/me`)
      .pipe(tap((profile) => this.updateSessionUser(profile)));
  }

  updateProfile(request: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${environment.apiBaseUrl}/me`, request)
      .pipe(tap((profile) => this.updateSessionUser(profile)));
  }

  logout(): void {
    if (this.isBrowser) {
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USER_KEY);
    }
    this.userSubject.next(null);
    this.profileSubject.next(null);
  }

  getToken(): string | null {
    if (!this.isBrowser) return null;
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (token && jwtExpired(token)) { this.logout(); return null; }
    return token;
  }

  isLoggedIn(): boolean { return !!this.getToken(); }

  hasRole(role: string): boolean { return this.userSubject.value?.role === role; }

  private saveSession(res: AuthResponse): void {
    if (!this.isBrowser) return;
    localStorage.setItem(this.TOKEN_KEY, res.token);
    const user: AuthUser = { username: res.username, role: res.role as 'CLIENT' | 'SELLER' };
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this.userSubject.next(user);
  }

  private updateSessionUser(profile: UserProfile): void {
    const user: AuthUser = { username: profile.username, role: profile.role };
    if (this.isBrowser) localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this.userSubject.next(user);
    this.profileSubject.next(profile);
  }

  private loadUser(): AuthUser | null {
    if (!this.isBrowser) return null;
    try {
      const raw = localStorage.getItem(this.USER_KEY);
      if (!raw) return null;
      const token = localStorage.getItem(this.TOKEN_KEY);
      if (token && jwtExpired(token)) {
        localStorage.removeItem(this.TOKEN_KEY);
        localStorage.removeItem(this.USER_KEY);
        return null;
      }
      return JSON.parse(raw) as AuthUser;
    } catch { return null; }
  }
}
