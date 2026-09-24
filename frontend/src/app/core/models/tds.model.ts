export type TdsRemittanceStatus =
  | 'DEDUCTED'
  | 'PAID_TO_ACCOUNTANT'
  | 'PAID_TO_IT_DEPARTMENT';

/**
 * Filter for the TDS list, summary, PDF and remittance-batch endpoints.
 * Absent/blank fields contribute no predicate on the backend and are
 * omitted from the serialized query string on the frontend.
 */
export interface TdsFilterRequest {
  deductionStartDate?: string; // ISO date (yyyy-MM-dd)
  deductionEndDate?: string;   // ISO date (yyyy-MM-dd)
  remittanceStartDate?: string; // ISO date (yyyy-MM-dd)
  remittanceEndDate?: string;   // ISO date (yyyy-MM-dd)
  vendorId?: number;
  vendorSearch?: string;
  statuses?: TdsRemittanceStatus[];
  tdsSection?: string;
  financialYear?: string;
}

export interface TdsLineDTO {
  voucherId: number;
  voucherNumber: string;
  deductionDate: string;
  financialYear: string;
  vendorId: number;
  vendorName: string;
  tdsSection: string;
  tdsRate: number;
  tdsAmount: number;
  status: TdsRemittanceStatus;
  remittanceId?: number;
  challanNumber?: string;
  paidToAccountantDate?: string;
  paidToItDate?: string;
}

export interface TdsSummaryDTO {
  totalDeducted: number;
  totalPaidToAccountant: number;
  totalPaidToItDept: number;
  totalPending: number;
  lineCount: number;
}

export interface TdsRemittanceDTO {
  remittanceId: number;
  batchReference: string;
  status: TdsRemittanceStatus;
  financialYear: string;
  totalAmount: number;
  accountantName: string;
  paidToAccountantDate: string;
  accountantReference?: string;
  challanNumber?: string;
  paidToItDate?: string;
  remarks?: string;
  lines: TdsLineDTO[];
}

export interface CreateRemittanceRequest {
  voucherIds: number[];
  accountantName: string;
  paidToAccountantDate: string; // ISO date (yyyy-MM-dd)
  accountantReference?: string;
  remarks?: string;
}

export interface RecordItRemittanceRequest {
  challanNumber: string;
  paidToItDate: string; // ISO date (yyyy-MM-dd)
  remarks?: string;
}
