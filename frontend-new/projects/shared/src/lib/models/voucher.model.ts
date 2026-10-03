export interface Voucher {
  voucherId: number;
  voucherNumber: string;
  voucherDate: string;
  voucherType: 'PAYMENT' | 'RECEIPT' | 'JOURNAL' | 'CONTRA';
  category: string;
  vendorId?: number;
  vendorName?: string;
  description: string;
  amount: number;
  paymentMode?: string;
  referenceNumber?: string;
  billInvoiceNumber?: string;
  billDate?: string;
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'FINAL' | 'CANCELLED';
  cancellationReason?: string;
  cancelledBy?: string;
  cancelledOn?: string;
  financialYear: string;
  // TDS fields
  tdsApplicable?: boolean;
  tdsSection?: string;
  tdsRate?: number;
  tdsAmount?: number;
  netPayable?: number;
  // Approval workflow
  viewedByTreasurer?: boolean;
  treasurerName?: string;
  treasurerViewedOn?: string;
  verifiedBySecretary?: boolean;
  secretaryName?: string;
  secretaryVerifiedOn?: string;
  approvedByChairman?: boolean;
  chairmanName?: string;
  chairmanApprovedOn?: string;
  documents?: VoucherDocument[];
  createdBy?: string;
  createdOn?: string;
  modifiedBy?: string;
  modifiedOn?: string;
}

export interface VoucherDocument {
  documentId: number;
  documentName: string;
  documentType: string;
  filePath: string;
  uploadedBy?: string;
  uploadedOn?: string;
}

export interface VoucherAudit {
  auditId: number;
  voucherId: number;
  voucherNumber: string;
  fieldChanged: string;
  oldValue?: string;
  newValue?: string;
  changeReason?: string;
  changedBy: string;
  changedOn: string;
  ipAddress?: string;
}

export interface VoucherCreateRequest {
  voucherDate: string;
  voucherType: 'PAYMENT' | 'RECEIPT' | 'JOURNAL' | 'CONTRA';
  category: string;
  vendorId?: number;
  description: string;
  amount: number;
  paymentMode?: string;
  referenceNumber?: string;
  billInvoiceNumber?: string;
  billDate?: string;
}

export interface VoucherUpdateRequest {
  category: string;
  vendorId?: number;
  description: string;
  amount: number;
  paymentMode?: string;
  referenceNumber?: string;
  billInvoiceNumber?: string;
  billDate?: string;
  updateReason: string;
}

export interface VoucherCancelRequest {
  cancellationReason: string;
}
