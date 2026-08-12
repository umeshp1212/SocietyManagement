import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '@env/environment';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  userId: number;
  username: string;
  fullName: string;
  roles: string[];
  permissions: string[];
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly apiUrl = `${environment.apiUrl}/auth`;
  private currentUserSubject = new BehaviorSubject<LoginResponse | null>(this.getStoredUser());

  currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/login`, request).pipe(
      tap(res => {
        if (res.success) {
          this.storeAuth(res.data);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('current_user');
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  changePassword(request: ChangePasswordRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/change-password`, request);
  }

  refreshToken(): Observable<any> {
    const refreshToken = this.getRefreshToken();
    return this.http.post<any>(`${this.apiUrl}/refresh-token`, { refreshToken }).pipe(
      tap(res => {
        if (res.success) {
          this.storeAuth(res.data);
        }
      })
    );
  }

  getMe(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/me`);
  }

  // ===== Token Management =====

  getToken(): string | null {
    return localStorage.getItem('access_token');
  }

  getRefreshToken(): string | null {
    return localStorage.getItem('refresh_token');
  }

  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) return false;
    // Simple expiry check from JWT payload
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  getCurrentUser(): LoginResponse | null {
    return this.currentUserSubject.value;
  }

  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    return user?.roles?.includes(role) || false;
  }

  hasAnyRole(roles: string[]): boolean {
    const user = this.getCurrentUser();
    return roles.some(role => user?.roles?.includes(role));
  }

  hasPermission(permission: string): boolean {
    const user = this.getCurrentUser();
    return user?.permissions?.includes(permission) || false;
  }

  // ===== Private =====

  private storeAuth(data: LoginResponse): void {
    localStorage.setItem('access_token', data.accessToken);
    localStorage.setItem('refresh_token', data.refreshToken);
    localStorage.setItem('current_user', JSON.stringify(data));
    this.currentUserSubject.next(data);
  }

  private getStoredUser(): LoginResponse | null {
    const stored = localStorage.getItem('current_user');
    if (stored) {
      try {
        return JSON.parse(stored);
      } catch {
        return null;
      }
    }
    return null;
  }
}
