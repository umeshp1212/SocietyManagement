import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse, PagedResponse } from '../models/api-response.model';
import { Vendor, VendorCreateRequest, VendorUpdateRequest, VendorDocument, VendorLedger } from '../models/vendor.model';

@Injectable({ providedIn: 'root' })
export class VendorService {

  private readonly apiUrl = `${environment.apiUrl}/vendors`;

  constructor(private http: HttpClient) {}

  createVendor(request: VendorCreateRequest): Observable<ApiResponse<Vendor>> {
    return this.http.post<ApiResponse<Vendor>>(this.apiUrl, request);
  }

  updateVendor(vendorId: number, request: VendorUpdateRequest): Observable<ApiResponse<Vendor>> {
    return this.http.put<ApiResponse<Vendor>>(`${this.apiUrl}/${vendorId}`, request);
  }

  getVendorById(vendorId: number): Observable<ApiResponse<Vendor>> {
    return this.http.get<ApiResponse<Vendor>>(`${this.apiUrl}/${vendorId}`);
  }

  getAllVendors(page = 0, size = 20, status?: string, category?: string, search?: string): Observable<ApiResponse<PagedResponse<Vendor>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (category) params = params.set('category', category);
    if (search) params = params.set('search', search);
    return this.http.get<ApiResponse<PagedResponse<Vendor>>>(this.apiUrl, { params });
  }

  getActiveVendorsList(): Observable<ApiResponse<Vendor[]>> {
    return this.http.get<ApiResponse<Vendor[]>>(`${this.apiUrl}/active-list`);
  }

  getExpiringContracts(days = 30): Observable<ApiResponse<Vendor[]>> {
    return this.http.get<ApiResponse<Vendor[]>>(`${this.apiUrl}/expiring`, {
      params: new HttpParams().set('days', days)
    });
  }

  getExpiredContracts(): Observable<ApiResponse<Vendor[]>> {
    return this.http.get<ApiResponse<Vendor[]>>(`${this.apiUrl}/expired`);
  }

  getVendorSummary(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/summary`);
  }

  getVendorDocuments(vendorId: number): Observable<ApiResponse<VendorDocument[]>> {
    return this.http.get<ApiResponse<VendorDocument[]>>(`${this.apiUrl}/${vendorId}/documents`);
  }

  getVendorLedger(vendorId: number, startDate?: string, endDate?: string): Observable<ApiResponse<VendorLedger>> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<ApiResponse<VendorLedger>>(`${this.apiUrl}/${vendorId}/ledger`, { params });
  }

  downloadLedgerPdf(vendorId: number, startDate?: string, endDate?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get(`${this.apiUrl}/${vendorId}/ledger/pdf`, {
      params,
      responseType: 'blob'
    });
  }
}
