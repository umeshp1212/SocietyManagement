import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse, PagedResponse } from '../models/api-response.model';
import { Voucher, VoucherCreateRequest, VoucherUpdateRequest, VoucherCancelRequest, VoucherAudit } from '../models/voucher.model';

@Injectable({ providedIn: 'root' })
export class VoucherService {

  private readonly apiUrl = `${environment.apiUrl}/vouchers`;

  constructor(private http: HttpClient) {}

  createVoucher(request: VoucherCreateRequest): Observable<ApiResponse<Voucher>> {
    return this.http.post<ApiResponse<Voucher>>(this.apiUrl, request);
  }

  updateVoucher(voucherId: number, request: VoucherUpdateRequest): Observable<ApiResponse<Voucher>> {
    return this.http.put<ApiResponse<Voucher>>(`${this.apiUrl}/${voucherId}`, request);
  }

  finalizeVoucher(voucherId: number): Observable<ApiResponse<Voucher>> {
    return this.http.patch<ApiResponse<Voucher>>(`${this.apiUrl}/${voucherId}/finalize`, {});
  }

  cancelVoucher(voucherId: number, request: VoucherCancelRequest): Observable<ApiResponse<Voucher>> {
    return this.http.patch<ApiResponse<Voucher>>(`${this.apiUrl}/${voucherId}/cancel`, request);
  }

  getVoucherById(voucherId: number): Observable<ApiResponse<Voucher>> {
    return this.http.get<ApiResponse<Voucher>>(`${this.apiUrl}/${voucherId}`);
  }

  getAllVouchers(page = 0, size = 20, type?: string, status?: string, category?: string,
                 financialYear?: string, startDate?: string, endDate?: string, search?: string): Observable<ApiResponse<PagedResponse<Voucher>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (type) params = params.set('type', type);
    if (status) params = params.set('status', status);
    if (category) params = params.set('category', category);
    if (financialYear) params = params.set('financialYear', financialYear);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (search) params = params.set('search', search);
    return this.http.get<ApiResponse<PagedResponse<Voucher>>>(this.apiUrl, { params });
  }

  getAuditTrail(voucherId: number): Observable<ApiResponse<VoucherAudit[]>> {
    return this.http.get<ApiResponse<VoucherAudit[]>>(`${this.apiUrl}/${voucherId}/audit-trail`);
  }

  getVoucherSummary(financialYear?: string): Observable<ApiResponse<any>> {
    let params = new HttpParams();
    if (financialYear) params = params.set('financialYear', financialYear);
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/summary`, { params });
  }

  getCategoryWiseReport(startDate: string, endDate: string): Observable<ApiResponse<any[]>> {
    const params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/reports/category-wise`, { params });
  }

  getVendorWiseReport(startDate: string, endDate: string): Observable<ApiResponse<any[]>> {
    const params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/reports/vendor-wise`, { params });
  }

  // ===== PDF =====
  getVoucherPdfUrl(voucherId: number): string {
    return `${this.apiUrl}/${voucherId}/pdf`;
  }

  getVoucherPdfViewUrl(voucherId: number): string {
    return `${this.apiUrl}/${voucherId}/pdf/view`;
  }

  getBulkPdfUrl(startDate?: string, endDate?: string, financialYear?: string): string {
    let url = `${this.apiUrl}/pdf/bulk?`;
    if (financialYear) {
      url += `financialYear=${financialYear}`;
    } else if (startDate && endDate) {
      url += `startDate=${startDate}&endDate=${endDate}`;
    }
    return url;
  }
}
