import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';

/** Read model for a visitor/delivery gate request (guard view). */
export interface GateVisitorRequest {
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
  notifiedRecipients?: string[];
}

/** Gate (guard) API for creating and tracking visitor/delivery entry requests. */
@Injectable({ providedIn: 'root' })
export class GateVisitorService {

  private readonly apiUrl = `${environment.apiUrl}/gate/visitor-requests`;

  constructor(private http: HttpClient) {}

  /**
   * Create a request. Sent as multipart so a photo can be attached. Either unitId or
   * unitNumber identifies the flat.
   */
  create(form: {
    unitId?: number | null;
    unitNumber?: string | null;
    visitorName: string;
    company?: string;
    purpose?: string;
    photo?: File | null;
  }): Observable<any> {
    const fd = new FormData();
    if (form.unitId != null) {
      fd.append('unitId', String(form.unitId));
    }
    if (form.unitNumber) {
      fd.append('unitNumber', form.unitNumber);
    }
    fd.append('visitorName', form.visitorName);
    if (form.company) {
      fd.append('company', form.company);
    }
    if (form.purpose) {
      fd.append('purpose', form.purpose);
    }
    if (form.photo) {
      fd.append('photo', form.photo);
    }
    return this.http.post<any>(this.apiUrl, fd);
  }

  getById(requestId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${requestId}`);
  }

  /** List requests; pass a status to filter, omit for the full gate history/log. */
  list(status?: string): Observable<any> {
    const url = status ? `${this.apiUrl}?status=${status}` : this.apiUrl;
    return this.http.get<any>(url);
  }

  photoUrl(photoPath?: string): string | null {
    if (!photoPath) {
      return null;
    }
    return `${environment.apiUrl}/files/view/${photoPath}`;
  }
}
