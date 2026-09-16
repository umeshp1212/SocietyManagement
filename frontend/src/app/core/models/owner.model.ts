export interface Owner {
  ownerId: number;
  fullName: string;
  contactNumber: string;
  alternateNumber?: string;
  email?: string;
  aadharNumber?: string;
  panNumber?: string;
  permanentAddress?: string;
  occupation?: string;
  photoPath?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  unitNumbers?: string;
  status: 'ACTIVE' | 'TRANSFERRED';
  createdBy?: string;
  createdOn?: string;
  modifiedBy?: string;
  modifiedOn?: string;
}

export interface OwnerCreateRequest {
  fullName: string;
  contactNumber: string;
  alternateNumber?: string;
  email?: string;
  aadharNumber?: string;
  panNumber?: string;
  permanentAddress?: string;
  occupation?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
}

export interface OwnerUpdateRequest extends OwnerCreateRequest {}

export interface Unit {
  unitId: number;
  unitNumber: string;
  wing?: string;
  floor?: string;
  unitType: 'FLAT' | 'SHOP';
  areaSqft?: number;
  monthlyMaintenanceAmount: number;
  primaryOwnerName?: string;
  allOwnerNames?: string;
  owners?: UnitOwner[];
  occupancyStatus: 'SELF_OCCUPIED' | 'RENTED' | 'VACANT';
  twoWheelerCount?: number;
  fourWheelerCount?: number;
  status: string;
}

export interface UnitOwner {
  id: number;
  ownerId: number;
  ownerName: string;
  ownerContact: string;
  isPrimary: boolean;
  ownershipPercentage: number;
  addedOn?: string;
}

export interface AddCoOwnerRequest {
  unitId: number;
  ownerId: number;
  isPrimary: boolean;
  ownershipPercentage: number;
}

export interface UnitCreateRequest {
  unitNumber: string;
  wing?: string;
  floor?: string;
  unitType: 'FLAT' | 'SHOP';
  areaSqft?: number;
  monthlyMaintenanceAmount: number;
}

export interface OwnershipHistory {
  historyId: number;
  unitId: number;
  unitNumber: string;
  ownerId: number;
  ownerName: string;
  ownershipStartDate: string;
  ownershipEndDate?: string;
  transferType: 'PURCHASE' | 'INHERITANCE' | 'GIFT' | 'COURT_ORDER';
  transferDocumentPath?: string;
  remarks?: string;
  recordedBy?: string;
  recordedOn?: string;
}

export interface OwnershipTransferRequest {
  unitId: number;
  newOwnerId: number;
  transferDate: string;
  transferType: 'PURCHASE' | 'INHERITANCE' | 'GIFT' | 'COURT_ORDER';
  remarks?: string;
}
export type RecipientScope = 'ALL' | 'SELECTED';

export type NotEmailedReason = 'MISSING_EMAIL' | 'SEND_FAILURE' | 'MAIL_NOT_CONFIGURED';

export interface SendOwnerEmailRequest {
  recipientScope: RecipientScope;
  ownerIds?: number[];
  subject: string;
  body: string;
}

export interface NotEmailedEntry {
  ownerId: number;
  ownerName: string;
  reason: NotEmailedReason;
}

export interface SendReport {
  totalAttempted: number;
  sentCount: number;
  notEmailedCount: number;
  mailConfigured: boolean;
  notEmailed: NotEmailedEntry[];
}
