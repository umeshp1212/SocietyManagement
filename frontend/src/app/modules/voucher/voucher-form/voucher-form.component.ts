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
import { VoucherService } from '@core/services/voucher.service';
import { VendorService } from '@core/services/vendor.service';
import { VoucherCategoryService } from '@core/services/voucher-category.service';
import { Vendor } from '@core/models/vendor.model';
import { VoucherCategory } from '@core/models/voucher-category.model';
import { environment } from '@env/environment';

@Component({
  selector: 'app-voucher-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatDatepickerModule, MatNativeDateModule, MatIconModule, MatSnackBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Update Voucher' : 'Create New Voucher' }}</mat-card-title>
          <span *ngIf="isEdit && voucherNumber" class="voucher-number">{{ voucherNumber }}</span>
        </mat-card-header>
        <mat-card-content>
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
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Vendor (optional)</mat-label>
                <mat-select formControlName="vendorId">
                  <mat-option [value]="null">-- None --</mat-option>
                  <mat-option *ngFor="let vendor of vendors" [value]="vendor.vendorId">
                    {{ vendor.vendorName }} ({{ vendor.categoryName }})
                  </mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <!-- Amount & Payment -->
            <h4>Payment Information</h4>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Amount (Rs) *</mat-label>
                <input matInput type="number" formControlName="amount" min="0.01">
                <mat-icon matPrefix>currency_rupee</mat-icon>
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
                <mat-label>Bill Date</mat-label>
                <input matInput [matDatepicker]="billPicker" formControlName="billDate">
                <mat-datepicker-toggle matSuffix [for]="billPicker"></mat-datepicker-toggle>
                <mat-datepicker #billPicker></mat-datepicker>
              </mat-form-field>
            </div>

            <!-- File Upload - Invoice/Bill -->
            <h4>Upload Invoice / Bill</h4>
            <div class="upload-section">
              <input type="file" #fileInput (change)="onFileSelected($event)"
                     accept=".pdf,.jpg,.jpeg,.png,.doc,.docx" style="display:none">
              <button mat-stroked-button type="button" (click)="fileInput.click()">
                <mat-icon>cloud_upload</mat-icon> Choose File
              </button>
              <span class="file-name" *ngIf="selectedFile">{{ selectedFile.name }}</span>
              <span class="file-hint" *ngIf="!selectedFile">PDF, JPG, PNG, DOC (max 10MB)</span>
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
            </mat-form-field>

            <!-- Update Reason (only in edit mode) -->
            <div *ngIf="isEdit">
              <h4>Reason for Update</h4>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Update Reason *</mat-label>
                <textarea matInput formControlName="updateReason" rows="2"
                          placeholder="Why is this voucher being updated?"></textarea>
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
    h4 { color: #1976d2; margin: 20px 0 10px; border-bottom: 1px solid #e0e0e0; padding-bottom: 5px; }
    .voucher-number { font-size: 14px; color: #666; font-weight: 500; }
    mat-card-header { display: flex; justify-content: space-between; align-items: center; }
    .upload-section { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    .file-name { font-weight: 500; color: #1976d2; }
    .file-hint { color: #999; font-size: 12px; }
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
  vendors: Vendor[] = [];
  selectedFile: File | null = null;
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
          description: v.description
        });

        // If voucher is FINAL, amount and vendor cannot be changed
        if (v.status === 'FINAL') {
          this.voucherForm.get('amount')?.disable();
          this.voucherForm.get('vendorId')?.disable();
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
      this.selectedFile = input.files[0];
    }
  }

  uploadFileForVoucher(voucherId: number): void {
    if (!this.selectedFile) return;

    const formData = new FormData();
    formData.append('file', this.selectedFile);
    formData.append('documentType', 'BILL');

    this.http.post<any>(`${environment.apiUrl}/vouchers/${voucherId}/documents/upload`, formData)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Invoice/Bill uploaded successfully', 'Close', { duration: 3000 });
          }
        },
        error: () => this.snackBar.open('File upload failed', 'Close', { duration: 3000 })
      });
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
        updateReason: formValue.updateReason
      };

      this.voucherService.updateVoucher(this.voucherId!, updateRequest).subscribe({
        next: (res) => {
          if (res.success) {
            // Upload file if selected
            if (this.selectedFile) {
              this.uploadFileForVoucher(this.voucherId!);
            }
            this.snackBar.open('Voucher updated successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/vouchers', this.voucherId]);
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
        billDate: formValue.billDate ? this.formatDate(formValue.billDate) : undefined
      };

      this.voucherService.createVoucher(createRequest).subscribe({
        next: (res) => {
          if (res.success) {
            // Upload file if selected
            if (this.selectedFile) {
              this.uploadFileForVoucher(res.data.voucherId);
            }
            this.snackBar.open(
              'Voucher ' + res.data.voucherNumber + ' created successfully',
              'Close', { duration: 3000 }
            );
            this.router.navigate(['/vouchers', res.data.voucherId]);
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Creation failed', 'Close', { duration: 5000 })
      });
    }
  }

  private formatDate(date: any): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toISOString().split('T')[0];
  }
}
