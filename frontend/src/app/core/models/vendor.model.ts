export interface Vendor {
  vendorId: number;
  vendorName: string;
  categoryId: number;
  contactPerson?: string;
  phone: string;
  email?: string;
  address?: string;
  panNumber?: string;
  gstNumber?: string;
  bankAccountNumber?: string;
  bankIfsc?: string;
  bankName?: string;
  agreementStartDate?: string;
  agreementEndDate?: string;
  contractedAmount?: number;
  paymentFrequency?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'BLACKLISTED';
  documents?: VendorDocument[];
  daysUntilExpiry?: number;
  isContractExpired?: boolean;
  createdBy?: string;
  createdOn?: string;
  modifiedBy?: string;
  modifiedOn?: string;
}

export interface VendorDocument {
  documentId: number;
  documentName: string;
  documentType: string;
  filePath: string;
  uploadedBy?: string;
  uploadedOn?: string;
}

export interface VendorCreateRequest {
  vendorName: string;
  category: string;
  contactPerson?: string;
  phone: string;
  email?: string;
  address?: string;
  panNumber?: string;
  gstNumber?: string;
  bankAccountNumber?: string;
  bankIfsc?: string;
  bankName?: string;
  agreementStartDate?: string;
  agreementEndDate?: string;
  contractedAmount?: number;
  paymentFrequency?: string;
}

export interface VendorUpdateRequest extends VendorCreateRequest {
  status: 'ACTIVE' | 'INACTIVE' | 'BLACKLISTED';
}

export interface VendorLedger {
  vendorId: number;
  vendorName: string;
  totalAmount: number;
  entries: LedgerEntry[];
}

export interface LedgerEntry {
  voucherId: number;
  voucherNumber: string;
  voucherDate: string;
  voucherType: string;
  category: string;
  description: string;
  amount: number;
  paymentMode?: string;
  referenceNumber?: string;
  status: string;
  financialYear: string;
  runningTotal: number;
}
