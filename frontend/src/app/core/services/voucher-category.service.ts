import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse } from '../models/api-response.model';
import { VoucherCategory, VoucherCategoryCreateRequest, VoucherCategoryUpdateRequest } from '../models/voucher-category.model';

@Injectable({ providedIn: 'root' })
export class VoucherCategoryService {

  private readonly apiUrl = `${environment.apiUrl}/voucher-categories`;

  constructor(private http: HttpClient) {}

  getAllCategories(): Observable<ApiResponse<VoucherCategory[]>> {
    return this.http.get<ApiResponse<VoucherCategory[]>>(this.apiUrl);
  }

  getActiveCategories(type?: string): Observable<ApiResponse<VoucherCategory[]>> {
    let params = new HttpParams();
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<ApiResponse<VoucherCategory[]>>(`${this.apiUrl}/active`, { params });
  }

  getCategoryById(id: number): Observable<ApiResponse<VoucherCategory>> {
    return this.http.get<ApiResponse<VoucherCategory>>(`${this.apiUrl}/${id}`);
  }

  createCategory(request: VoucherCategoryCreateRequest): Observable<ApiResponse<VoucherCategory>> {
    return this.http.post<ApiResponse<VoucherCategory>>(this.apiUrl, request);
  }

  updateCategory(id: number, request: VoucherCategoryUpdateRequest): Observable<ApiResponse<VoucherCategory>> {
    return this.http.put<ApiResponse<VoucherCategory>>(`${this.apiUrl}/${id}`, request);
  }

  deleteCategory(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.apiUrl}/${id}`);
  }
}
