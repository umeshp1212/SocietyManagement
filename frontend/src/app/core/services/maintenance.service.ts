import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse, PagedResponse } from '../models/api-response.model';

@Injectable({ providedIn: 'root' })
export class MaintenanceService {

  private readonly apiUrl = `${environment.apiUrl}/maintenance`;

  constructor(private http: HttpClient) {}

  // ======================== CHARGE CONFIG ========================

  getChargeConfigs(): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/charge-config`);
  }

  getAllChargeConfigs(): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/charge-config/all`);
  }

  getChargeConfigById(id: number): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/charge-config/${id}`);
  }

  createChargeConfig(config: any): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/charge-config`, config);
  }

  updateChargeConfig(id: number, config: any): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(`${this.apiUrl}/charge-config/${id}`, config);
  }

  deleteChargeConfig(id: number): Observable<ApiResponse<any>> {
    return this.http.delete<ApiResponse<any>>(`${this.apiUrl}/charge-config/${id}`);
  }

  generateBills(month: number, year: number, dueDayOfMonth?: number, regenerate?: boolean): Observable<ApiResponse<any>> {
    const body: any = { month, year };
    if (dueDayOfMonth) body.dueDayOfMonth = dueDayOfMonth;
    if (regenerate) body.regenerate = true;
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/bills/generate`, body);
  }

  getBillsByMonth(month: number, year: number, page = 0, size = 20): Observable<ApiResponse<PagedResponse<any>>> {
    const params = new HttpParams()
      .set('month', month)
      .set('year', year)
      .set('page', page)
      .set('size', size);
    return this.http.get<ApiResponse<PagedResponse<any>>>(`${this.apiUrl}/bills`, { params });
  }

  getBillById(billId: number): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/bills/${billId}`);
  }

  getBillsByUnit(unitId: number): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/bills/unit/${unitId}`);
  }

  getOutstandingByUnit(unitId: number): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/bills/outstanding/${unitId}`);
  }

  getDefaulters(month: number, year: number): Observable<ApiResponse<any[]>> {
    const params = new HttpParams().set('month', month).set('year', year);
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/bills/defaulters`, { params });
  }

  getCollectionSummary(month: number, year: number): Observable<ApiResponse<any>> {
    const params = new HttpParams().set('month', month).set('year', year);
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/bills/collection-summary`, { params });
  }

  recordOfflinePayment(request: any): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/payments/offline`, request);
  }

  getPaymentsByUnit(unitId: number, page = 0, size = 20): Observable<ApiResponse<PagedResponse<any>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PagedResponse<any>>>(`${this.apiUrl}/payments/unit/${unitId}`, { params });
  }

  getPaymentsByBill(billId: number): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/payments/bill/${billId}`);
  }

  generatePaymentLink(billId: number): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/bills/${billId}/payment-link`, {});
  }

  getWhatsAppLink(billId: number): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/bills/${billId}/whatsapp-link`);
  }

  getQrCode(billId: number): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/bills/${billId}/qr-code`);
  }

  // ======================== PDF DOWNLOADS ========================

  downloadBillPdf(billId: number): void {
    this.http.get(`${this.apiUrl}/bills/${billId}/pdf`, { responseType: 'blob' })
      .subscribe(blob => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `Bill_${billId}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      });
  }

  downloadBulkBillsPdf(month: number, year: number): void {
    this.http.get(`${this.apiUrl}/bills/pdf/bulk?month=${month}&year=${year}`, { responseType: 'blob' })
      .subscribe(blob => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `Bills_${month}_${year}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      });
  }

  // ======================== PENALTIES ========================

  addPenalty(penalty: any): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/penalties`, penalty);
  }

  getPenaltiesByUnit(unitId: number): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/penalties/unit/${unitId}`);
  }

  getPendingPenalties(): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/penalties/pending`);
  }

  getPenaltiesByMonth(month: number, year: number): Observable<ApiResponse<any[]>> {
    const params = new HttpParams().set('month', month).set('year', year);
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/penalties`, { params });
  }

  cancelPenalty(penaltyId: number): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(`${this.apiUrl}/penalties/${penaltyId}/cancel`, {});
  }

  // ======================== WATER CHARGE CONFIG ========================

  getWaterChargeConfig(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/water-charge-config`);
  }

  saveWaterChargeConfig(config: any): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/water-charge-config`, config);
  }

  previewWaterCharges(): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/water-charge-config/preview`);
  }
}
