import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse, PagedResponse } from '../models/api-response.model';
import { Tenant, TenantCreateRequest, TenantUpdateRequest, MoveOutRequest, TenantDocument } from '../models/tenant.model';

@Injectable({ providedIn: 'root' })
export class TenantService {

  private readonly apiUrl = `${environment.apiUrl}/tenants`;

  constructor(private http: HttpClient) {}

  registerTenant(request: TenantCreateRequest): Observable<ApiResponse<Tenant>> {
    return this.http.post<ApiResponse<Tenant>>(this.apiUrl, request);
  }

  updateTenant(tenantId: number, request: TenantUpdateRequest): Observable<ApiResponse<Tenant>> {
    return this.http.put<ApiResponse<Tenant>>(`${this.apiUrl}/${tenantId}`, request);
  }

  getTenantById(tenantId: number): Observable<ApiResponse<Tenant>> {
    return this.http.get<ApiResponse<Tenant>>(`${this.apiUrl}/${tenantId}`);
  }

  getAllTenants(page = 0, size = 20, status?: string, nocStatus?: string, search?: string): Observable<ApiResponse<PagedResponse<Tenant>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (nocStatus) params = params.set('nocStatus', nocStatus);
    if (search) params = params.set('search', search);
    return this.http.get<ApiResponse<PagedResponse<Tenant>>>(this.apiUrl, { params });
  }

  updateNocStatus(tenantId: number, nocStatus: string, remarks?: string): Observable<ApiResponse<Tenant>> {
    return this.http.patch<ApiResponse<Tenant>>(`${this.apiUrl}/${tenantId}/noc`, { nocStatus, remarks });
  }

  updatePoliceVerification(tenantId: number, status: string): Observable<ApiResponse<Tenant>> {
    return this.http.patch<ApiResponse<Tenant>>(
      `${this.apiUrl}/${tenantId}/police-verification`,
      null,
      { params: new HttpParams().set('status', status) }
    );
  }

  markNoticePeriod(tenantId: number): Observable<ApiResponse<Tenant>> {
    return this.http.patch<ApiResponse<Tenant>>(`${this.apiUrl}/${tenantId}/notice-period`, {});
  }

  moveOutTenant(tenantId: number, request: MoveOutRequest): Observable<ApiResponse<Tenant>> {
    return this.http.patch<ApiResponse<Tenant>>(`${this.apiUrl}/${tenantId}/move-out`, request);
  }

  getTenantHistoryByUnit(unitId: number): Observable<ApiResponse<Tenant[]>> {
    return this.http.get<ApiResponse<Tenant[]>>(`${this.apiUrl}/unit/${unitId}/history`);
  }

  getActiveTenantByUnit(unitId: number): Observable<ApiResponse<Tenant>> {
    return this.http.get<ApiResponse<Tenant>>(`${this.apiUrl}/unit/${unitId}/active`);
  }

  getExpiringAgreements(days = 30): Observable<ApiResponse<Tenant[]>> {
    return this.http.get<ApiResponse<Tenant[]>>(`${this.apiUrl}/expiring-agreements`, {
      params: new HttpParams().set('days', days)
    });
  }

  getPendingPoliceVerification(): Observable<ApiResponse<Tenant[]>> {
    return this.http.get<ApiResponse<Tenant[]>>(`${this.apiUrl}/pending-police-verification`);
  }

  getTenantSummary(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/summary`);
  }
}
