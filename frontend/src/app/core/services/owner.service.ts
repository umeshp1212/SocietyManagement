import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse, PagedResponse } from '../models/api-response.model';
import { Owner, OwnerCreateRequest, OwnerUpdateRequest, Unit, UnitCreateRequest, UnitOwner, AddCoOwnerRequest, OwnershipHistory, OwnershipTransferRequest } from '../models/owner.model';

@Injectable({ providedIn: 'root' })
export class OwnerService {

  private readonly apiUrl = `${environment.apiUrl}/owners`;
  private readonly unitUrl = `${environment.apiUrl}/units`;

  constructor(private http: HttpClient) {}

  // ===== OWNERS =====
  createOwner(request: OwnerCreateRequest): Observable<ApiResponse<Owner>> {
    return this.http.post<ApiResponse<Owner>>(this.apiUrl, request);
  }

  updateOwner(ownerId: number, request: OwnerUpdateRequest): Observable<ApiResponse<Owner>> {
    return this.http.put<ApiResponse<Owner>>(`${this.apiUrl}/${ownerId}`, request);
  }

  getOwnerById(ownerId: number): Observable<ApiResponse<Owner>> {
    return this.http.get<ApiResponse<Owner>>(`${this.apiUrl}/${ownerId}`);
  }

  getAllOwners(page = 0, size = 20, status?: string, search?: string): Observable<ApiResponse<PagedResponse<Owner>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (search) params = params.set('search', search);
    return this.http.get<ApiResponse<PagedResponse<Owner>>>(this.apiUrl, { params });
  }

  getActiveOwnersList(): Observable<ApiResponse<Owner[]>> {
    return this.http.get<ApiResponse<Owner[]>>(`${this.apiUrl}/active-list`);
  }

  transferOwnership(request: OwnershipTransferRequest): Observable<ApiResponse<OwnershipHistory>> {
    return this.http.post<ApiResponse<OwnershipHistory>>(`${this.apiUrl}/transfer`, request);
  }

  getHistoryByUnit(unitId: number): Observable<ApiResponse<OwnershipHistory[]>> {
    return this.http.get<ApiResponse<OwnershipHistory[]>>(`${this.apiUrl}/history/unit/${unitId}`);
  }

  getHistoryByOwner(ownerId: number): Observable<ApiResponse<OwnershipHistory[]>> {
    return this.http.get<ApiResponse<OwnershipHistory[]>>(`${this.apiUrl}/history/owner/${ownerId}`);
  }

  // ===== UNITS =====
  createUnit(request: UnitCreateRequest): Observable<ApiResponse<Unit>> {
    return this.http.post<ApiResponse<Unit>>(this.unitUrl, request);
  }

  updateUnit(unitId: number, request: UnitCreateRequest): Observable<ApiResponse<Unit>> {
    return this.http.put<ApiResponse<Unit>>(`${this.unitUrl}/${unitId}`, request);
  }

  getUnitById(unitId: number): Observable<ApiResponse<Unit>> {
    return this.http.get<ApiResponse<Unit>>(`${this.unitUrl}/${unitId}`);
  }

  getAllUnits(page = 0, size = 20, wing?: string, unitType?: string, occupancyStatus?: string): Observable<ApiResponse<PagedResponse<Unit>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (wing) params = params.set('wing', wing);
    if (unitType) params = params.set('unitType', unitType);
    if (occupancyStatus) params = params.set('occupancyStatus', occupancyStatus);
    return this.http.get<ApiResponse<PagedResponse<Unit>>>(this.unitUrl, { params });
  }

  getUnitsByOwner(ownerId: number): Observable<ApiResponse<Unit[]>> {
    return this.http.get<ApiResponse<Unit[]>>(`${this.unitUrl}/by-owner/${ownerId}`);
  }

  getOccupancySummary(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.unitUrl}/summary`);
  }

  // ===== CO-OWNER MANAGEMENT =====
  addOwnerToUnit(request: AddCoOwnerRequest): Observable<ApiResponse<Unit>> {
    return this.http.post<ApiResponse<Unit>>(`${this.unitUrl}/owners`, request);
  }

  removeOwnerFromUnit(unitId: number, ownerId: number): Observable<ApiResponse<Unit>> {
    return this.http.delete<ApiResponse<Unit>>(`${this.unitUrl}/${unitId}/owners/${ownerId}`);
  }

  getUnitOwners(unitId: number): Observable<ApiResponse<UnitOwner[]>> {
    return this.http.get<ApiResponse<UnitOwner[]>>(`${this.unitUrl}/${unitId}/owners`);
  }
}
