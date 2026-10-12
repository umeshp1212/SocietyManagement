import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';

/** Read model for a visitor/delivery gate request (member view). */
export interface VisitorRequest {
  visitorRequestId: number;
  unitId: number;
  unitNumber: string;
  wing?: string;
  floor?: string;
  visitorName: string;
  company?: string;
  purpose?: string;
  photoPath?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'TIMED_OUT';
  createdBy?: string;
  createdOn?: string;
  decidedBy?: string;
  decidedOn?: string;
  rejectionReason?: string;
  expiresAt?: string;
}

/**
 * Member-portal API for the visitor/delivery approval workflow and for registering the
 * device's FCM token. All calls go through the member path so the member Bearer token
 * is attached by the auth interceptor.
 */
@Injectable({ providedIn: 'root' })
export class VisitorService {

  private readonly apiUrl = `${environment.apiUrl}/member`;

  constructor(private http: HttpClient) {}

  /** Pending visitor requests across the logged-in owner's flats. */
  getPending(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/visitor-requests`);
  }

  approve(requestId: number): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/visitor-requests/${requestId}/approve`, {});
  }

  reject(requestId: number, reason?: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/visitor-requests/${requestId}/reject`, { reason });
  }

  /** Register this device's FCM token so the owner receives push notifications. */
  registerDeviceToken(token: string, platform = 'WEB'): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/device-tokens`, { token, platform });
  }

  /** Absolute URL to a visitor photo stored under the backend upload dir. */
  photoUrl(photoPath?: string): string | null {
    if (!photoPath) {
      return null;
    }
    return `${environment.apiUrl}/files/view/${photoPath}`;
  }
}
