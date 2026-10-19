import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import { VoucherService } from '@core/services/voucher.service';
import { VendorService } from '@core/services/vendor.service';
import { VoucherCategoryService } from '@core/services/voucher-category.service';
import { Vendor } from '@core/models/vendor.model';
import { VoucherCategory } from '@core/models/voucher-category.model';
import { SearchableSelectComponent } from '@shared/components/searchable-select';
import { BackButtonComponent } from '@shared/components/back-button';
import { environment } from '@env/environment';

@Component({
  selector: 'app-voucher-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatDatepickerModule, MatNativeDateModule, MatIconModule, MatSnackBarModule,
    MatTooltipModule, SearchableSelectComponent, BackButtonComponent
  ],
  template: `
    <div class="form-container">
      <app-back-button link="/vouchers" label="Back to Vouchers"></app-back-button>
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Update Voucher' : 'Create New Voucher' }}</mat-card-title>
          <span *ngIf="isEdit && voucherNumber" class="voucher-number">{{ voucherNumber }}</span>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="isEdit && voucherStatus === 'FINAL'" class="final-notice">
            <mat-icon>lock</mat-icon>
            <span>
              This voucher is <strong>FINAL</strong>. Amount and vendor are locked to protect the
              accounting record. To correct a mistake, cancel this voucher and create a new one.
            </span>
          </div>

          <form [formGroup]="voucherForm" (ngSubmit)="onSubmit()">

            <!-- Voucher Type & Date -->
            <h4>Voucher Details</h4>
            <div class="form-row">
              <mat-form-field appearance="outline" *ngIf="!isEdit">
                <mat-label>Voucher Type *</mat-label>
                <mat-select formControlName="voucherType">
                  <mat-option value="PAYMENT">Payment Voucher</mat-option>
                  <mat-option value="RECEIPT">Receipt Voucher</mat-option>
                  <mat-option value="JOURNAL">Journal Voucher</mat-option>
                  <mat-option value="CONTRA">Contra Voucher</mat-option>
                </mat-select>
                <mat-error *ngIf="voucherForm.get('voucherType')?.hasError('required')">Voucher type is required</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline" *ngIf="isEdit">
                <mat-label>Voucher Type</mat-label>
                <input matInput [value]="voucherForm.get('voucherType')?.value" disabled>
              </mat-form-field>
              <mat-form-field appearance="outline" *ngIf="!isEdit">
                <mat-label>Voucher Date *</mat-label>
                <input matInput [matDatepicker]="datePicker" formControlName="voucherDate">
                <mat-datepicker-toggle matSuffix [for]="datePicker"></mat-datepicker-toggle>
                <mat-datepicker #datePicker></mat-datepicker>
                <mat-error *ngIf="voucherForm.get('voucherDate')?.hasError('required')">Voucher date is required</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline" *ngIf="isEdit">
                <mat-label>Voucher Date</mat-label>
                <input matInput [value]="voucherForm.get('voucherDate')?.value" disabled>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Category *</mat-label>
                <mat-select formControlName="category">
                  <mat-optgroup label="Expenses">
                    <mat-option *ngFor="let cat of expenseCategories" [value]="cat.value">
                      {{ cat.label }}
                    </mat-option>
                  </mat-optgroup>
                  <mat-optgroup label="Income">
                    <mat-option *ngFor="let cat of incomeCategories" [value]="cat.value">
                      {{ cat.label }}
                    </mat-option>
                  </mat-optgroup>
                </mat-select>
                <mat-error *ngIf="voucherForm.get('category')?.hasError('required')">Category is required</mat-error>
              </mat-form-field>
              <app-searchable-select
                formControlName="vendorId"
                label="Vendor (optional)"
                placeholder="Search vendor..."
                [options]="vendors"
                valueKey="vendorId"
                [labelWith]="vendorLabel"
                [allowClear]="true">
              </app-searchable-select>
            </div>

            <!-- Amount & Payment -->
            <h4>Payment Information</h4>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Amount (Rs) *</mat-label>
                <input matInput type="number" formControlName="amount" min="0.01">
                <mat-icon matPrefix>currency_rupee</mat-icon>
                <mat-error *ngIf="voucherForm.get('amount')?.hasError('required')">Amount is required</mat-error>
                <mat-error *ngIf="voucherForm.get('amount')?.hasError('min')">Amount must be greater than 0</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Payment Mode</mat-label>
                <mat-select formControlName="paymentMode">
                  <mat-option [value]="null">-- Select --</mat-option>
                  <mat-option value="CASH">Cash</mat-option>
                  <mat-option value="CHEQUE">Cheque</mat-option>
                  <mat-option value="UPI">UPI</mat-option>
                  <mat-option value="NEFT">NEFT</mat-option>
                  <mat-option value="RTGS">RTGS</mat-option>
                  <mat-option value="IMPS">IMPS</mat-option>
                  <mat-option value="BANK_TRANSFER">Bank Transfer</mat-option>
                  <mat-option value="ONLINE">Online</mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Cheque No / Transaction ID</mat-label>
                <input matInput formControlName="referenceNumber"
                       placeholder="Cheque number, UTR, or UPI Ref">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Bill / Invoice Number</mat-label>
                <input matInput formControlName="billInvoiceNumber">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Pay To / Cheque in Favour Of *</mat-label>
                <input matInput formControlName="payToName"
                       placeholder="Name the cheque is made out to">
                <mat-icon matSuffix matTooltip="The name the cheque is made out to. Often the registered vendor name, but enter a different name when the vendor asks the cheque in favour of their proprietor, sister concern, or a third party. The registered vendor stays on record for the bill.">info_outline</mat-icon>
                <mat-hint>Usually the vendor name; change it if the payee differs</mat-hint>
                <mat-error *ngIf="voucherForm.get('payToName')?.hasError('required')">Pay To name is required</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Bill Date</mat-label>
                <input matInput [matDatepicker]="billPicker" formControlName="billDate">
                <mat-datepicker-toggle matSuffix [for]="billPicker"></mat-datepicker-toggle>
                <mat-datepicker #billPicker></mat-datepicker>
              </mat-form-field>
            </div>

            <!-- File Upload - Invoice/Bill -->
            <h4>Upload Invoice / Bill</h4>
            <div class="upload-section">
              <input type="file" #fileInput (change)="onFileSelected($event)" multiple
                     accept=".pdf,.jpg,.jpeg,.png,.doc,.docx" style="display:none">
              <button mat-stroked-button type="button" (click)="fileInput.click()">
                <mat-icon>cloud_upload</mat-icon> Choose File(s)
              </button>
              <span class="file-hint">PDF, JPG, PNG, DOC (max 10MB each)</span>
            </div>
            <div *ngIf="selectedFiles.length > 0" class="selected-list">
              <div *ngFor="let file of selectedFiles; let i = index" class="selected-item">
                <mat-icon>description</mat-icon>
                <span class="selected-name">{{ file.name }}</span>
                <span class="selected-size">{{ (file.size / 1024) | number:'1.0-0' }} KB</span>
                <button mat-icon-button type="button" color="warn"
                        (click)="removeSelectedFile(i)" matTooltip="Remove file">
                  <mat-icon>close</mat-icon>
                </button>
              </div>
            </div>
            <div *ngIf="uploadedDocuments.length > 0" class="uploaded-list">
              <p><strong>Uploaded Documents:</strong></p>
              <div *ngFor="let doc of uploadedDocuments" class="uploaded-item">
                <mat-icon>description</mat-icon>
                <span>{{ doc.documentName }} ({{ doc.documentType }})</span>
                <span class="upload-date">{{ doc.uploadedOn | date:'dd-MM-yyyy HH:mm' }}</span>
              </div>
            </div>

            <!-- Description -->
            <h4>Description / Narration</h4>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Description *</mat-label>
              <textarea matInput formControlName="description" rows="3"
                        placeholder="Purpose of this voucher entry"></textarea>
              <mat-error *ngIf="voucherForm.get('description')?.hasError('required')">Description is required</mat-error>
            </mat-form-field>

            <!-- Update Reason (only in edit mode) -->
            <div *ngIf="isEdit">
              <h4>Reason for Update</h4>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Update Reason *</mat-label>
                <textarea matInput formControlName="updateReason" rows="2"
                          placeholder="Why is this voucher being updated?"></textarea>
                <mat-error *ngIf="voucherForm.get('updateReason')?.hasError('required')">Update reason is required</mat-error>
              </mat-form-field>
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/vouchers">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="voucherForm.invalid">
                {{ isEdit ? 'Update Voucher' : 'Create Voucher' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .full-width { width: 100%; }
    .final-notice { display: flex; align-items: flex-start; gap: 8px; background: #fff4e5; color: #663c00; border: 1px solid #ffcc80; border-radius: 8px; padding: 12px; margin-bottom: 16px; font-size: 13px; }
    .final-notice mat-icon { font-size: 20px; height: 20px; width: 20px; }
    h4 { color: #1976d2; margin: 20px 0 10px; border-bottom: 1px solid #e0e0e0; padding-bottom: 5px; }
    .voucher-number { font-size: 14px; color: #666; font-weight: 500; }
    mat-card-header { display: flex; justify-content: space-between; align-items: center; }
    .upload-section { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
    .file-name { font-weight: 500; color: #1976d2; }
    .file-hint { color: #999; font-size: 12px; }
    .selected-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
    .selected-item { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #e3f2fd; border: 1px solid #bbdefb; border-radius: 8px; font-size: 13px; }
    .selected-item mat-icon { font-size: 18px; height: 18px; width: 18px; color: #1976d2; }
    .selected-name { font-weight: 500; color: #1565c0; flex: 1; word-break: break-all; }
    .selected-size { color: #777; font-size: 12px; }
    .uploaded-list { background: #f5f5f5; padding: 12px; border-radius: 8px; margin-bottom: 16px; }
    .uploaded-list p { margin: 0 0 8px; }
    .uploaded-item { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 13px; }
    .uploaded-item mat-icon { font-size: 18px; height: 18px; width: 18px; color: #666; }
    .upload-date { color: #999; margin-left: auto; font-size: 12px; }
  `]
})
export class VoucherFormComponent implements OnInit {
  voucherForm!: FormGroup;
  isEdit = false;
  voucherId?: number;
  voucherNumber = '';
  voucherStatus = '';
  vendors: Vendor[] = [];

  /** Display label for a vendor option in the searchable dropdown. */
  readonly vendorLabel = (v: Vendor): string => `${v.vendorName} (${v.categoryName})`;
  selectedFiles: File[] = [];
  uploadedDocuments: any[] = [];

  expenseCategories: { value: string; label: string }[] = [];
  incomeCategories: { value: string; label: string }[] = [];

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private voucherService: VoucherService,
    private vendorService: VendorService,
    private categoryService: VoucherCategoryService,
    private route: ActivatedRoute,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.voucherForm = this.fb.group({
      voucherType: ['PAYMENT', Validators.required],
      voucherDate: [new Date(), Validators.required],
      category: ['', Validators.required],
      vendorId: [null],
      amount: [null, [Validators.required, Validators.min(0.01)]],
      paymentMode: [null],
      referenceNumber: [''],
      billInvoiceNumber: [''],
      billDate: [null],
      payToName: ['', Validators.required],
      description: ['', Validators.required],
      updateReason: ['']
    });

    // Load active vendors for dropdown
    this.vendorService.getActiveVendorsList().subscribe(res => {
      if (res.success) this.vendors = res.data;
    });

    // Load categories from API
    this.categoryService.getActiveCategories().subscribe(res => {
      if (res.success) {
        this.expenseCategories = res.data
          .filter(c => c.type === 'EXPENSE')
          .map(c => ({ value: c.code, label: c.name }));
        this.incomeCategories = res.data
          .filter(c => c.type === 'INCOME')
          .map(c => ({ value: c.code, label: c.name }));
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.voucherId = +id;
      this.voucherForm.get('updateReason')?.setValidators(Validators.required);
      this.voucherForm.get('updateReason')?.updateValueAndValidity();
      this.loadVoucher();
    }
  }

  loadVoucher(): void {
    this.voucherService.getVoucherById(this.voucherId!).subscribe(res => {
      if (res.success) {
        const v = res.data;
        this.voucherNumber = v.voucherNumber;
        this.voucherStatus = v.status;
        this.voucherForm.patchValue({
          voucherType: v.voucherType,
          voucherDate: v.voucherDate,
          category: v.category,
          vendorId: v.vendorId || null,
          amount: v.amount,
          paymentMode: v.paymentMode || null,
          referenceNumber: v.referenceNumber || '',
          billInvoiceNumber: v.billInvoiceNumber || '',
          billDate: v.billDate ? new Date(v.billDate) : null,
          payToName: v.payToName || '',
          description: v.description
        });

        // If voucher is FINAL, amount and vendor cannot be changed (backend enforces this).
        // To correct a FINAL voucher, cancel it and create a new one.
        if (v.status === 'FINAL') {
          this.voucherForm.get('amount')?.disable();
          this.voucherForm.get('vendorId')?.disable();
          // Older FINAL vouchers created before this field existed may have no
          // Pay To value. Don't let a required validator block editing them.
          if (!v.payToName) {
            this.voucherForm.get('payToName')?.clearValidators();
            this.voucherForm.get('payToName')?.updateValueAndValidity();
          }
        }

        // Load existing documents in edit mode
        this.loadDocuments();
      }
    });
  }

  loadDocuments(): void {
    if (!this.voucherId) return;
    this.http.get<any>(`${environment.apiUrl}/vouchers/${this.voucherId}/documents`)
      .subscribe(res => {
        if (res.success) this.uploadedDocuments = res.data;
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      // Append newly chosen files, skipping duplicates (same name + size).
      Array.from(input.files).forEach(file => {
        const exists = this.selectedFiles.some(
          f => f.name === file.name && f.size === file.size
        );
        if (!exists) this.selectedFiles.push(file);
      });
      // Reset the input so selecting the same file again re-triggers change.
      input.value = '';
    }
  }

  removeSelectedFile(index: number): void {
    this.selectedFiles.splice(index, 1);
  }

  /**
   * Uploads every selected file for the given voucher. Returns an observable
   * that completes only after ALL uploads finish, so callers can defer
   * navigation until the attachments are actually persisted.
   */
  private uploadFilesForVoucher(voucherId: number) {
    const uploads = this.selectedFiles.map(file => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('documentType', 'BILL');
      return this.http.post<any>(
        `${environment.apiUrl}/vouchers/${voucherId}/documents/upload`, formData
      );
    });
    return forkJoin(uploads);
  }

  onSubmit(): void {
    if (this.voucherForm.invalid) return;

    const formValue = this.voucherForm.getRawValue();

    if (this.isEdit) {
      const updateRequest = {
        category: formValue.category,
        vendorId: formValue.vendorId,
        description: formValue.description,
        amount: formValue.amount,
        paymentMode: formValue.paymentMode,
        referenceNumber: formValue.referenceNumber || undefined,
        billInvoiceNumber: formValue.billInvoiceNumber || undefined,
        billDate: formValue.billDate ? this.formatDate(formValue.billDate) : undefined,
        payToName: formValue.payToName || undefined,
        updateReason: formValue.updateReason
      };

      this.voucherService.updateVoucher(this.voucherId!, updateRequest).subscribe({
        next: (res) => {
          if (res.success) {
            this.finishSubmit(this.voucherId!, 'Voucher updated successfully');
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Update failed', 'Close', { duration: 5000 })
      });
    } else {
      const createRequest = {
        voucherType: formValue.voucherType,
        voucherDate: this.formatDate(formValue.voucherDate),
        category: formValue.category,
        vendorId: formValue.vendorId,
        description: formValue.description,
        amount: formValue.amount,
        paymentMode: formValue.paymentMode,
        referenceNumber: formValue.referenceNumber || undefined,
        billInvoiceNumber: formValue.billInvoiceNumber || undefined,
        billDate: formValue.billDate ? this.formatDate(formValue.billDate) : undefined,
        payToName: formValue.payToName || undefined
      };

      this.voucherService.createVoucher(createRequest).subscribe({
        next: (res) => {
          if (res.success) {
            this.finishSubmit(
              res.data.voucherId,
              'Voucher ' + res.data.voucherNumber + ' created successfully'
            );
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Creation failed', 'Close', { duration: 5000 })
      });
    }
  }

  /**
   * Uploads any selected files, then navigates to the voucher detail page.
   * Navigation is deferred until the uploads complete so the detail page's
   * document list reflects the files that were just attached. If an upload
   * fails, we still navigate but warn the user.
   */
  private finishSubmit(voucherId: number, successMessage: string): void {
    if (this.selectedFiles.length === 0) {
      this.snackBar.open(successMessage, 'Close', { duration: 3000 });
      this.router.navigate(['/vouchers', voucherId]);
      return;
    }

    this.uploadFilesForVoucher(voucherId).subscribe({
      next: () => {
        this.selectedFiles = [];
        this.snackBar.open(successMessage, 'Close', { duration: 3000 });
        this.router.navigate(['/vouchers', voucherId]);
      },
      error: () => {
        this.snackBar.open(
          'Voucher saved, but one or more files failed to upload. You can attach them from the voucher details page.',
          'Close', { duration: 6000 }
        );
        this.router.navigate(['/vouchers', voucherId]);
      }
    });
  }

  private formatDate(date: any): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toISOString().split('T')[0];
  }
}
