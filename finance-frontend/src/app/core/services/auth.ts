import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoginResponse } from '../models/login-response';
import { LoginRequest } from '../models/login-request';

export interface CurrentUser {
  id: number;
  username: string;
  fullName: string;
  role: string
}

@Injectable({
  providedIn: 'root',
})
export class Auth {
  private apiUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      request
    );
  }

  saveToken(token: string): void {
    localStorage.setItem('token', token);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  logout(): void {
    localStorage.removeItem('token');
  }

    getCurrentUser(): CurrentUser | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return {
        id:       payload.id,
        username: payload.sub,
        fullName: payload.fullName,
        role:     payload.role,
      };
    } catch {
      return null;
    }
  }

  getRole(): string | null {
    return this.getCurrentUser()?.role ?? null;
  }

  getUsername(): string | null {
    return this.getCurrentUser()?.username ?? null;
  }

  get isDemo(): boolean {
    const u = this.getUsername();
    return u === 'demo_admin' || u === 'demo_acc' || u === 'demo_owner';
  }

}
