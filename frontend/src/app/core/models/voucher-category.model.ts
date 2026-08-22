export interface VoucherCategory {
  categoryId: number;
  code: string;
  name: string;
  type: 'EXPENSE' | 'INCOME';
  description?: string;
  displayOrder: number;
  isActive: boolean;
  createdBy?: string;
  createdOn?: string;
  modifiedBy?: string;
  modifiedOn?: string;
}

export interface VoucherCategoryCreateRequest {
  code: string;
  name: string;
  type: 'EXPENSE' | 'INCOME';
  description?: string;
  displayOrder?: number;
}

export interface VoucherCategoryUpdateRequest {
  code: string;
  name: string;
  type: 'EXPENSE' | 'INCOME';
  description?: string;
  displayOrder?: number;
  isActive?: boolean;
}
