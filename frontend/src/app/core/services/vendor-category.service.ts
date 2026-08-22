import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse } from '../models/api-response.model';
import { VendorCategoryModel, VendorCategoryCreateRequest, VendorCategoryUpdateRequest } from '../models/vendor-category.model';

@Injectable({ providedIn: 'root' })
export class VendorCategoryService {

  private readonly apiUrl = `${environment.apiUrl}/vendor-categories`;

  constructor(private http: HttpClient) {}

  getAllCategories(): Observable<ApiResponse<VendorCategoryModel[]>> {
    return this.http.get<ApiResponse<VendorCategoryModel[]>>(this.apiUrl);
  }

  getActiveCategories(): Observable<ApiResponse<VendorCategoryModel[]>> {
    return this.http.get<ApiResponse<VendorCategoryModel[]>>(`${this.apiUrl}/active`);
  }

  getCategoryById(id: number): Observable<ApiResponse<VendorCategoryModel>> {
    return this.http.get<ApiResponse<VendorCategoryModel>>(`${this.apiUrl}/${id}`);
  }

  createCategory(request: VendorCategoryCreateRequest): Observable<ApiResponse<VendorCategoryModel>> {
    return this.http.post<ApiResponse<VendorCategoryModel>>(this.apiUrl, request);
  }

  updateCategory(id: number, request: VendorCategoryUpdateRequest): Observable<ApiResponse<VendorCategoryModel>> {
    return this.http.put<ApiResponse<VendorCategoryModel>>(`${this.apiUrl}/${id}`, request);
  }

  deleteCategory(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.apiUrl}/${id}`);
  }
}
