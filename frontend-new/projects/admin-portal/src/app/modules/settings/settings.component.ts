import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormGroup, FormBuilder, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSnackBarModule
  ],
  template: `
    <div class="settings-container">
      <h2>Society Settings</h2>

      <form [formGroup]="settingsForm" (ngSubmit)="onSave()">

        <!-- Society Identity -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Society Identity</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="form-row">
              <mat-form-field class="full-width">
                <mat-label>Society Name</mat-label>
                <input matInput formControlName="societyName" />
                <mat-error *ngIf="settingsForm.get('societyName')?.hasError('required')">
                  Society Name is required
                </mat-error>
              </mat-form-field>
            </div>
            <div class="form-row">
              <mat-form-field>
                <mat-label>Registration Number</mat-label>
                <input matInput formControlName="registrationNumber" />
                <mat-error *ngIf="settingsForm.get('registrationNumber')?.hasError('required')">
                  Registration Number is required
                </mat-error>
              </mat-form-field>
              <mat-form-field>
                <mat-label>Registration Date</mat-label>
                <input matInput type="date" formControlName="registrationDate" />
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Address -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Address</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="form-row">
              <mat-form-field class="full-width">
                <mat-label>Address Line 1</mat-label>
                <input matInput formControlName="addressLine1" />
              </mat-form-field>
            </div>
            <div class="form-row">
              <mat-form-field class="full-width">
                <mat-label>Address Line 2</mat-label>
                <input matInput formControlName="addressLine2" />
              </mat-form-field>
            </div>
            <div class="form-row">
              <mat-form-field>
                <mat-label>City</mat-label>
                <input matInput formControlName="city" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>State</mat-label>
                <input matInput formControlName="state" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Pincode</mat-label>
                <input matInput formControlName="pincode" />
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Contact -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Contact</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="form-row">
              <mat-form-field>
                <mat-label>Phone</mat-label>
                <input matInput formControlName="phone" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Email</mat-label>
                <input matInput type="email" formControlName="email" />
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Office Bearers -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Office Bearers</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="form-row">
              <mat-form-field>
                <mat-label>Chairman Name</mat-label>
                <input matInput formControlName="chairmanName" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Secretary Name</mat-label>
                <input matInput formControlName="secretaryName" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Treasurer Name</mat-label>
                <input matInput formControlName="treasurerName" />
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Payment Gateway -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Payment Gateway</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="gateway-hint">Select the payment gateway for online maintenance payments on the member portal.</p>
            <div class="form-row">
              <mat-form-field>
                <mat-label>Active Gateway</mat-label>
                <mat-select formControlName="paymentGateway">
                  <mat-option value="RAZORPAY">Razorpay</mat-option>
                  <mat-option value="CASHFREE">Cashfree</mat-option>
                </mat-select>
              </mat-form-field>
            </div>
            <p class="gateway-note" *ngIf="settingsForm.get('paymentGateway')?.value === 'RAZORPAY'">
              Razorpay credentials are configured in application settings (environment variables).
            </p>
            <p class="gateway-note" *ngIf="settingsForm.get('paymentGateway')?.value === 'CASHFREE'">
              Cashfree credentials are configured in application settings (environment variables).
            </p>
          </mat-card-content>
        </mat-card>

        <!-- Online Payment Discount -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Online Payment Discount</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="gateway-hint">Encourage members to pay maintenance online by offering an early payment discount.</p>

            <div class="form-row">
              <mat-slide-toggle formControlName="discountEnabled" color="primary">
                Enable Online Payment Discount
              </mat-slide-toggle>
            </div>

            <div *ngIf="settingsForm.get('discountEnabled')?.value" class="discount-fields">
              <div class="form-row">
                <mat-form-field>
                  <mat-label>Discount Percentage (%)</mat-label>
                  <input matInput type="number" formControlName="discountPercent" min="0" max="100" step="0.5">
                  <mat-hint>e.g., 2 means 2% off</mat-hint>
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Discount Valid For (Days)</mat-label>
                  <input matInput type="number" formControlName="discountDueDays" min="1" max="30">
                  <mat-hint>Days from bill date to avail discount</mat-hint>
                </mat-form-field>
              </div>
              <div class="form-row">
                <mat-form-field class="full-width">
                  <mat-label>Promotional Message</mat-label>
                  <input matInput formControlName="discountMessage" maxlength="500">
                  <mat-hint>Shown to members on their dashboard</mat-hint>
                </mat-form-field>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <div class="form-actions">
          <button mat-raised-button color="primary" type="submit" [disabled]="settingsForm.invalid">
            <mat-icon>save</mat-icon> Save Settings
          </button>
        </div>
      </form>

      <!-- NOC Types -->
      <mat-card class="noc-card">
        <mat-card-header>
          <mat-card-title>NOC Types</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="noc-header">
            <p class="gateway-hint">
              Configure the types of No Objection Certificate owners can request. The default template
              pre-fills the certificate body at approval (admin can still edit per request).
              Placeholders: <code>{{ '{' }}ownerName{{ '}' }} {{ '{' }}unitNumber{{ '}' }} {{ '{' }}societyName{{ '}' }} {{ '{' }}registrationNumber{{ '}' }} {{ '{' }}addressee{{ '}' }} {{ '{' }}details{{ '}' }} {{ '{' }}date{{ '}' }}</code>
            </p>
            <button mat-raised-button color="primary" type="button" (click)="showAddForm = !showAddForm">
              <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
              {{ showAddForm ? 'Cancel' : 'Add NOC Type' }}
            </button>
          </div>

          <!-- Add Form -->
          <mat-card *ngIf="showAddForm" class="noc-add-card" appearance="outlined">
            <mat-card-content>
              <div class="form-row">
                <mat-form-field>
                  <mat-label>Code</mat-label>
                  <input matInput [(ngModel)]="newType.code" placeholder="LOAN_TRANSFER">
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Name</mat-label>
                  <input matInput [(ngModel)]="newType.name" placeholder="Loan / Loan Transfer">
                </mat-form-field>
                <mat-form-field class="narrow">
                  <mat-label>Order</mat-label>
                  <input matInput type="number" [(ngModel)]="newType.displayOrder">
                </mat-form-field>
              </div>
              <mat-form-field class="full-width">
                <mat-label>Description</mat-label>
                <input matInput [(ngModel)]="newType.description">
              </mat-form-field>
              <mat-form-field class="full-width">
                <mat-label>Default Template</mat-label>
                <textarea matInput [(ngModel)]="newType.defaultTemplate" rows="4"></textarea>
              </mat-form-field>
              <div class="form-actions">
                <button mat-raised-button color="primary" type="button" (click)="addType()"
                        [disabled]="!newType.code || !newType.name">
                  <mat-icon>save</mat-icon> Save
                </button>
              </div>
            </mat-card-content>
          </mat-card>

          <!-- List -->
          <mat-card *ngFor="let t of types" class="noc-type-card" appearance="outlined">
            <mat-card-content>
              <div class="type-head">
                <div>
                  <strong>{{ t.name }}</strong> <span class="code">({{ t.code }})</span>
                  <span class="chip" [class.inactive]="!t.isActive">{{ t.isActive ? 'Active' : 'Inactive' }}</span>
                </div>
                <mat-slide-toggle [(ngModel)]="t.isActive" (change)="saveType(t)" color="primary"></mat-slide-toggle>
              </div>
              <p class="desc" *ngIf="t.description">{{ t.description }}</p>
              <mat-form-field class="full-width">
                <mat-label>Default Template</mat-label>
                <textarea matInput [(ngModel)]="t.defaultTemplate" rows="3"></textarea>
              </mat-form-field>
              <div class="form-actions">
                <button mat-button color="primary" type="button" (click)="saveType(t)"><mat-icon>save</mat-icon> Save</button>
              </div>
            </mat-card-content>
          </mat-card>

          <div *ngIf="types.length === 0" class="empty">No NOC types configured yet.</div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .settings-container {
      padding: 20px;
      max-width: 900px;
      margin: 0 auto;
    }

    mat-card {
      margin-bottom: 20px;
    }

    .full-width {
      width: 100%;
    }

    .form-row {
      display: flex;
      gap: 16px;
    }

    .form-row mat-form-field {
      flex: 1;
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }

    .gateway-hint { color: #666; font-size: 13px; margin-bottom: 12px; }
    .gateway-note { color: #888; font-size: 12px; font-style: italic; margin-top: -8px; }
    .discount-fields { margin-top: 16px; }

    .noc-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
    .noc-header .gateway-hint { flex: 1; }
    .noc-header code { background: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-size: 12px; }
    .noc-add-card, .noc-type-card { margin: 12px 0; }
    .narrow { flex: 0.4 !important; }
    .type-head { display: flex; justify-content: space-between; align-items: center; }
    .code { color: #888; font-size: 13px; }
    .chip { font-size: 11px; padding: 2px 8px; border-radius: 12px; background: #e8f5e9; color: #2e7d32; margin-left: 8px; }
    .chip.inactive { background: #ffebee; color: #c62828; }
    .desc { color: #666; font-size: 13px; margin: 6px 0; }
    .empty { text-align: center; color: #999; padding: 40px; }
  `]
})
export class SettingsComponent implements OnInit {
  settingsForm: FormGroup;

  // NOC Types management
  types: any[] = [];
  showAddForm = false;
  newType: any = { code: '', name: '', description: '', defaultTemplate: '', displayOrder: 0, isActive: true };

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private snackBar: MatSnackBar
  ) {
    this.settingsForm = this.fb.group({
      societyName: ['', Validators.required],
      registrationNumber: ['', Validators.required],
      registrationDate: [''],
      addressLine1: [''],
      addressLine2: [''],
      city: [''],
      state: [''],
      pincode: [''],
      phone: [''],
      email: [''],
      chairmanName: [''],
      secretaryName: [''],
      treasurerName: [''],
      paymentGateway: ['RAZORPAY'],
      discountEnabled: [false],
      discountPercent: [0],
      discountDueDays: [10],
      discountMessage: ['Pay online before the due date and get a discount!']
    });
  }

  ngOnInit(): void {
    this.http.get<{ success: boolean; data: any }>(`${environment.apiUrl}/settings`).subscribe({
      next: (response) => {
        if (response.success && response.data) {
          this.settingsForm.patchValue(response.data);
        }
      },
      error: (err) => {
        this.snackBar.open('Failed to load settings', 'Close', { duration: 3000 });
      }
    });

    this.loadNocTypes();
  }

  loadNocTypes(): void {
    this.http.get<any>(`${environment.apiUrl}/noc-types`).subscribe({
      next: (res) => { if (res.success) this.types = res.data || []; },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to load NOC types', 'Close', { duration: 4000 })
    });
  }

  addType(): void {
    this.http.post<any>(`${environment.apiUrl}/noc-types`, this.newType).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('NOC type created', 'Close', { duration: 3000 });
          this.newType = { code: '', name: '', description: '', defaultTemplate: '', displayOrder: 0, isActive: true };
          this.showAddForm = false;
          this.loadNocTypes();
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to create', 'Close', { duration: 4000 })
    });
  }

  saveType(t: any): void {
    this.http.put<any>(`${environment.apiUrl}/noc-types/${t.nocTypeId}`, {
      name: t.name, description: t.description, defaultTemplate: t.defaultTemplate,
      displayOrder: t.displayOrder, isActive: t.isActive
    }).subscribe({
      next: (res) => { if (res.success) this.snackBar.open('Saved', 'Close', { duration: 2000 }); },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to save', 'Close', { duration: 4000 })
    });
  }

  onSave(): void {
    if (this.settingsForm.invalid) {
      return;
    }

    this.http.put<{ success: boolean }>(`${environment.apiUrl}/settings`, this.settingsForm.value).subscribe({
      next: (response) => {
        this.snackBar.open('Settings saved successfully', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.snackBar.open('Failed to save settings', 'Close', { duration: 3000 });
      }
    });
  }
}
