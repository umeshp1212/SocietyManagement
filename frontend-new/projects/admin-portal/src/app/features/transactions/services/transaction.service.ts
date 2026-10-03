import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '@env/environment';
import {
  ApiResponse,
  PagedResponse,
  TransactionDetail,
  TransactionFilter,
  TransactionSummary,
} from '../models/transaction.models';

/**
 * Client for the read-only Transaction Page endpoints.
 *
 * Wraps the two secured backend endpoints:
 *  - GET /transactions        (filtered, paged list)
 *  - GET /transactions/{id}   (single transaction detail)
 *
 * Both endpoints return the shared ApiResponse envelope; this service unwraps
 * the envelope and emits only the `data` payload to callers.
 */
@Injectable({ providedIn: 'root' })
export class TransactionService {

  private readonly apiUrl = `${environment.apiUrl}/transactions`;

  constructor(private http: HttpClient) {}

  /**
   * Fetch a filtered, paged list of transactions.
   *
   * The filter object is translated into query params: only params that are
   * actually set (non-null, non-undefined, non-blank) are included. The
   * `statuses` array is sent as REPEATED `status` query params to match the
   * backend's repeatable request parameter.
   */
  listTransactions(filter: TransactionFilter = {}): Observable<PagedResponse<TransactionSummary>> {
    const params = this.buildParams(filter);
    return this.http
      .get<ApiResponse<PagedResponse<TransactionSummary>>>(this.apiUrl, { params })
      .pipe(map(response => response.data));
  }

  /** Fetch the detail view for a single transaction by its payment id. */
  getTransaction(id: number): Observable<TransactionDetail> {
    return this.http
      .get<ApiResponse<TransactionDetail>>(`${this.apiUrl}/${id}`)
      .pipe(map(response => response.data));
  }

  /**
   * Translate a TransactionFilter into HttpParams.
   *
   * Rules:
   *  - undefined / null / blank string values are skipped
   *  - `statuses` becomes repeated `status` params (one per value)
   *  - `page` and `size` are included when present
   */
  private buildParams(filter: TransactionFilter): HttpParams {
    let params = new HttpParams();

    params = this.appendIfPresent(params, 'startDate', filter.startDate);
    params = this.appendIfPresent(params, 'endDate', filter.endDate);
    params = this.appendIfPresent(params, 'paymentMode', filter.paymentMode);
    params = this.appendIfPresent(params, 'payerType', filter.payerType);
    params = this.appendIfPresent(params, 'unitId', filter.unitId);
    params = this.appendIfPresent(params, 'unitSearch', filter.unitSearch);
    params = this.appendIfPresent(params, 'reference', filter.reference);
    params = this.appendIfPresent(params, 'page', filter.page);
    params = this.appendIfPresent(params, 'size', filter.size);

    if (filter.statuses) {
      for (const status of filter.statuses) {
        if (this.isPresent(status)) {
          params = params.append('status', status);
        }
      }
    }

    return params;
  }

  private appendIfPresent(params: HttpParams, key: string, value: unknown): HttpParams {
    if (this.isPresent(value)) {
      return params.set(key, String(value));
    }
    return params;
  }

  private isPresent(value: unknown): boolean {
    if (value === undefined || value === null) {
      return false;
    }
    if (typeof value === 'string') {
      return value.trim().length > 0;
    }
    return true;
  }
}
