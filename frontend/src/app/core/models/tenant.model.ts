export interface Tenant {
  tenantId: number;
  unitId: number;
  unitNumber: string;
  ownerName?: string;
  tenantName: string;
  contactNumber: string;
  email?: string;
  aadharNumber?: string;
  panNumber?: string;
  permanentAddress?: string;
  photoPath?: string;
  rentStartDate: string;
  rentEndDate?: string;
  monthlyRentAmount?: number;
  securityDeposit?: number;
  agreementDocumentPath?: string;
  policeVerificationStatus: string;
  policeVerificationDocumentPath?: string;
  nocStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
  nocDocumentPath?: string;
  nocApprovedBy?: string;
  nocApprovedOn?: string;
  status: 'ACTIVE' | 'NOTICE_PERIOD' | 'VACATED';
  moveOutDate?: string;
  moveOutReason?: string;
  familyMembers: FamilyMember[];
  vehicles: Vehicle[];
  documents: TenantDocument[];
  createdBy?: string;
  createdOn?: string;
  daysUntilAgreementExpiry?: number;
  isAgreementExpired?: boolean;
}

export interface FamilyMember {
  memberId?: number;
  memberName: string;
  age?: number;
  relation: string;
  aadharNumber?: string;
  contactNumber?: string;
}

export interface Vehicle {
  vehicleId?: number;
  vehicleType: string;
  vehicleNumber: string;
  parkingSlot?: string;
}

export interface TenantDocument {
  documentId: number;
  documentName: string;
  documentType: string;
  filePath: string;
  uploadedBy?: string;
  uploadedOn?: string;
}

export interface TenantCreateRequest {
  unitId: number;
  tenantName: string;
  contactNumber: string;
  email?: string;
  aadharNumber?: string;
  panNumber?: string;
  permanentAddress?: string;
  rentStartDate: string;
  rentEndDate?: string;
  monthlyRentAmount?: number;
  securityDeposit?: number;
  familyMembers?: FamilyMember[];
  vehicles?: Vehicle[];
}

export interface TenantUpdateRequest {
  tenantName: string;
  contactNumber: string;
  email?: string;
  aadharNumber?: string;
  panNumber?: string;
  permanentAddress?: string;
  rentEndDate?: string;
  monthlyRentAmount?: number;
  securityDeposit?: number;
  familyMembers?: FamilyMember[];
  vehicles?: Vehicle[];
}

export interface MoveOutRequest {
  moveOutDate: string;
  moveOutReason?: string;
}
