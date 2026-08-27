import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '@env/environment';

export interface MemberUnitInfo {
  unitId: number;
  unitNumber: string;
  wing: string;
  floor: string;
}

export interface MemberLoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  ownerId: number;
  ownerName: string;
  phone: string;
  email: string;
  units: MemberUnitInfo[];
}

export interface MemberDashboard {
  ownerId: number;
  ownerName: string;
  unitId: number;
  unitNumber: string;
  wing: string;
  floor: string;
  totalOutstanding: number;
  outstandingBillCount: number;
  totalPaid: number;
  outstandingBills: any[];
  recentPayments: any[];
}

export interface PaymentOrderResponse {
  razorpayOrderId: string;
  amount: number;
  currency: string;
  razorpayKeyId: string;
  receipt: string;
  ownerName: string;
  email: string;
  phone: string;
  unitNumber: string;
  description: string;
}

@Injectable({ providedIn: 'root' })
export class MemberAuthService {

  private readonly apiUrl = `${environment.apiUrl}/member`;
  private currentMemberSubject = new BehaviorSubject<MemberLoginResponse | null>(this.getStoredMember());

  currentMember$ = this.currentMemberSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {}

  // ===== OTP Auth =====

  sendOtp(phone: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/send-otp`, { phone });
  }

  verifyOtp(phone: string, otp: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/verify-otp`, { phone, otp }).pipe(
      tap(res => {
        if (res.success) {
          this.storeMemberAuth(res.data);
        }
      })
    );
  }

  // ===== Dashboard =====

  getDashboard(unitId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/maintenance/dashboard/${unitId}`);
  }

  getBills(unitId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/maintenance/bills/${unitId}`);
  }

  getOutstandingBills(unitId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/maintenance/outstanding/${unitId}`);
  }

  getPaymentHistory(unitId: number, page = 0, size = 20): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/maintenance/payments/${unitId}`, {
      params: { page: page.toString(), size: size.toString() }
    });
  }

  // ===== Payments =====

  createPaymentOrder(unitId: number, amount: number, billId?: number): Observable<any> {
    const body: any = { unitId, amount };
    if (billId) body.billId = billId;
    return this.http.post<any>(`${this.apiUrl}/payments/create-order`, body);
  }

  verifyPayment(data: {
    razorpayOrderId: string;
    razorpayPaymentId: string;
    razorpaySignature: string;
    unitId: number;
    amount: number;
    billId?: number;
  }): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/payments/verify`, data);
  }

  // ===== Token Management =====

  getToken(): string | null {
    return localStorage.getItem('member_access_token');
  }

  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  getCurrentMember(): MemberLoginResponse | null {
    return this.currentMemberSubject.value;
  }

  getSelectedUnit(): MemberUnitInfo | null {
    const stored = localStorage.getItem('member_selected_unit');
    if (stored) {
      try { return JSON.parse(stored); } catch { return null; }
    }
    // Default to first unit
    const member = this.getCurrentMember();
    if (member?.units?.length) {
      this.selectUnit(member.units[0]);
      return member.units[0];
    }
    return null;
  }

  selectUnit(unit: MemberUnitInfo): void {
    localStorage.setItem('member_selected_unit', JSON.stringify(unit));
  }

  logout(): void {
    localStorage.removeItem('member_access_token');
    localStorage.removeItem('member_refresh_token');
    localStorage.removeItem('member_user');
    localStorage.removeItem('member_selected_unit');
    this.currentMemberSubject.next(null);
    this.router.navigate(['/member-login']);
  }

  // ===== Private =====

  private storeMemberAuth(data: MemberLoginResponse): void {
    localStorage.setItem('member_access_token', data.accessToken);
    localStorage.setItem('member_refresh_token', data.refreshToken);
    localStorage.setItem('member_user', JSON.stringify(data));
    this.currentMemberSubject.next(data);

    // Auto-select first unit
    if (data.units?.length) {
      this.selectUnit(data.units[0]);
    }
  }

  private getStoredMember(): MemberLoginResponse | null {
    const stored = localStorage.getItem('member_user');
    if (stored) {
      try { return JSON.parse(stored); } catch { return null; }
    }
    return null;
  }
}
