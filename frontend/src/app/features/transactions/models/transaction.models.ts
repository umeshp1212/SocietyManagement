// TypeScript models for the Transaction Page feature.
// These mirror the backend DTOs and response envelopes so the frontend
// service and components can consume the /transactions endpoints in a
// type-safe way. See design.md "Frontend TypeScript models (mirror DTOs)".

export type PaymentMode =
  | 'CASHFREE_LINK'
  | 'CASHFREE_QR'
  | 'RAZORPAY'
  | 'UPI'
  | 'GPAY'
  | 'PHONEPE'
  | 'NEFT'
  | 'RTGS'
  | 'IMPS'
  | 'CHEQUE'
  | 'CASH'
  | 'BANK_TRANSFER';

export type TransactionStatus =
  | 'PENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'VERIFIED'
  | 'REVERSED';

export type PayerType = 'OWNER' | 'TENANT';

/** Filter/query parameters for GET /transactions. */
export interface TransactionFilter {
  startDate?: string;
  endDate?: string;
  paymentMode?: PaymentMode;
  statuses?: TransactionStatus[];
  payerType?: PayerType;
  unitId?: number;
  unitSearch?: string;
  reference?: string;
  page?: number;
  size?: number;
}

/** List response element (mirrors TransactionSummaryDTO). */
export interface TransactionSummary {
  paymentId: number;
  unitNumber: string;
  payerName: string;
  payerType: PayerType;
  amount: number;
  paymentDate: string;
  paymentMode: PaymentMode;
  status: TransactionStatus;
  transactionId: string;
  receiptNumber: string;
}

/** Detail response (mirrors TransactionDetailDTO). Superset of the summary. */
export interface TransactionDetail extends TransactionSummary {
  originalAmount?: number;
  discountAmount?: number;
  discountPercent?: number;
  remarks?: string;
  verifiedOn?: string;
  verifiedBy?: string;
  reversedOn?: string;
  reversedBy?: string;
  reversalReason?: string;
}

/** Mirrors the backend PagedResponse<T>. */
export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Mirrors the shared backend ApiResponse<T> envelope. */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}
