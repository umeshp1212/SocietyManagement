export interface VendorCategoryModel {
  categoryId: number;
  code: string;
  name: string;
  description?: string;
  displayOrder: number;
  isActive: boolean;
  createdBy?: string;
  createdOn?: string;
  modifiedBy?: string;
  modifiedOn?: string;
}

export interface VendorCategoryCreateRequest {
  code: string;
  name: string;
  description?: string;
  displayOrder?: number;
}

export interface VendorCategoryUpdateRequest {
  code: string;
  name: string;
  description?: string;
  displayOrder?: number;
  isActive?: boolean;
}
