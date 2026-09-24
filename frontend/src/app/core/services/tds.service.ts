import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { ApiResponse, PagedResponse } from '../models/api-response.model';
import {
  TdsFilterRequest,
  TdsLineDTO,
  TdsSummaryDTO,
  TdsRemittanceDTO,
  CreateRemittanceRequest,
  RecordItRemittanceRequest
} from '../models/tds.model';

@Injectable({ providedIn: 'root' })
export class TdsService {

  private readonly apiUrl = `${environment.apiUrl}/tds`;

  constructor(private http: HttpClient) {}

  /**
   * Serialize a TdsFilterRequest to HttpParams, skipping absent/blank fields.
   * The `statuses` array is appended as repeatable params.
   */
  private toFilterParams(filter?: TdsFilterRequest, base: HttpParams = new HttpParams()): HttpParams {
    let params = base;
    if (!filter) {
      return params;
    }

    params = this.appendString(params, 'deductionStartDate', filter.deductionStartDate);
    params = this.appendString(params, 'deductionEndDate', filter.deductionEndDate);
    params = this.appendString(params, 'remittanceStartDate', filter.remittanceStartDate);
    params = this.appendString(params, 'remittanceEndDate', filter.remittanceEndDate);
    params = this.appendString(params, 'vendorSearch', filter.vendorSearch);
    params = this.appendString(params, 'tdsSection', filter.tdsSection);
    params = this.appendString(params, 'financialYear', filter.financialYear);

    if (filter.vendorId !== null && filter.vendorId !== undefined) {
      params = params.set('vendorId', filter.vendorId);
    }

    if (filter.statuses && filter.statuses.length > 0) {
      for (const status of filter.statuses) {
        if (status !== null && status !== undefined && `${status}`.trim() !== '') {
          params = params.append('statuses', status);
        }
      }
    }

    return params;
  }

  private appendString(params: HttpParams, key: string, value?: string): HttpParams {
    if (value !== null && value !== undefined && value.trim() !== '') {
      return params.set(key, value.trim());
    }
    return params;
  }

  /** Filtered, paginated list of deducted-TDS lines across all vendors. */
  getTdsLines(
    filter?: TdsFilterRequest,
    page = 0,
    size = 20
  ): Observable<ApiResponse<PagedResponse<TdsLineDTO>>> {
    const params = this.toFilterParams(
      filter,
      new HttpParams().set('page', page).set('size', size)
    );
    return this.http.get<ApiResponse<PagedResponse<TdsLineDTO>>>(`${this.apiUrl}/lines`, { params });
  }

  /** Aggregated totals over the same filtered set. */
  getSummary(filter?: TdsFilterRequest): Observable<ApiResponse<TdsSummaryDTO>> {
    const params = this.toFilterParams(filter);
    return this.http.get<ApiResponse<TdsSummaryDTO>>(`${this.apiUrl}/summary`, { params });
  }

  /** Download the filtered TDS list + summary as a PDF blob. */
  downloadTdsPdf(filter?: TdsFilterRequest): Observable<Blob> {
    const params = this.toFilterParams(filter);
    return this.http.get(`${this.apiUrl}/lines/pdf`, {
      params,
      responseType: 'blob'
    });
  }

  /** Filtered, paginated remittance batches. */
  getRemittances(
    filter?: TdsFilterRequest,
    page = 0,
    size = 20
  ): Observable<ApiResponse<PagedResponse<TdsRemittanceDTO>>> {
    const params = this.toFilterParams(
      filter,
      new HttpParams().set('page', page).set('size', size)
    );
    return this.http.get<ApiResponse<PagedResponse<TdsRemittanceDTO>>>(`${this.apiUrl}/remittances`, { params });
  }

  /** Single remittance batch with its lines. */
  getRemittance(remittanceId: number): Observable<ApiResponse<TdsRemittanceDTO>> {
    return this.http.get<ApiResponse<TdsRemittanceDTO>>(`${this.apiUrl}/remittances/${remittanceId}`);
  }

  /** Stage 1: create a remittance batch (paid to accountant). */
  createRemittance(request: CreateRemittanceRequest): Observable<ApiResponse<TdsRemittanceDTO>> {
    return this.http.post<ApiResponse<TdsRemittanceDTO>>(`${this.apiUrl}/remittances`, request);
  }

  /** Stage 2: record the IT Department remittance for a batch. */
  recordItRemittance(
    remittanceId: number,
    request: RecordItRemittanceRequest
  ): Observable<ApiResponse<TdsRemittanceDTO>> {
    return this.http.patch<ApiResponse<TdsRemittanceDTO>>(
      `${this.apiUrl}/remittances/${remittanceId}/it-remittance`,
      request
    );
  }
}
