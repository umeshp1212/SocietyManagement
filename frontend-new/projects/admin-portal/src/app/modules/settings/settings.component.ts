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

        <!-- Maintenance Bill Settings -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Maintenance Bill Settings</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="gateway-hint">
              Controls the due date and late fee applied when bills are generated (manually or
              automatically).
            </p>
            <div class="form-row">
              <mat-form-field>
                <mat-label>Bill Due Day of Month</mat-label>
                <mat-select formControlName="billDueDay">
                  <mat-option [value]="0">Last day of month</mat-option>
                  <mat-option *ngFor="let d of daysOfMonth" [value]="d">{{ ordinal(d) }}</mat-option>
                </mat-select>
                <mat-hint>When the maintenance bill falls due each month</mat-hint>
              </mat-form-field>
              <mat-form-field>
                <mat-label>Late Fee (&#8377;)</mat-label>
                <input matInput type="number" formControlName="lateFeeAmount" min="0" step="1">
                <mat-hint>Flat fee charged once when a unit carries arrears. 0 = disabled</mat-hint>
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Automated Bill Generation -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Automated Bill Generation</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="gateway-hint">
              When started, the system generates each month's maintenance bills automatically on
              the configured day. Existing bills are never touched &mdash; only missing bills are
              created. Use the button to start or stop the automation.
            </p>

            <div class="toggle-row">
              <div>
                <strong>Status:</strong>
                <span class="chip" [class.inactive]="!settingsForm.get('autoBillGenerationEnabled')?.value">
                  {{ settingsForm.get('autoBillGenerationEnabled')?.value ? 'Running' : 'Stopped' }}
                </span>
              </div>
              <button mat-raised-button type="button"
                      [color]="settingsForm.get('autoBillGenerationEnabled')?.value ? 'warn' : 'primary'"
                      (click)="toggleAndSave('autoBillGenerationEnabled')">
                <mat-icon>{{ settingsForm.get('autoBillGenerationEnabled')?.value ? 'stop' : 'play_arrow' }}</mat-icon>
                {{ settingsForm.get('autoBillGenerationEnabled')?.value ? 'Stop Automation' : 'Start Automation' }}
              </button>
            </div>

            <div class="form-row">
              <mat-form-field>
                <mat-label>Generation Day of Month</mat-label>
                <mat-select formControlName="billGenerationDay">
                  <mat-option [value]="0">Last day of month</mat-option>
                  <mat-option *ngFor="let d of daysOfMonth" [value]="d">{{ ordinal(d) }}</mat-option>
                </mat-select>
                <mat-hint>Bills are generated on this date every month</mat-hint>
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Automated Payment Reminders -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Automated Payment Reminders</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="gateway-hint">
              When started, members with outstanding dues get an email reminder on the configured
              day, with a link to pay online via the member portal. Make sure email (SMTP) is
              configured and owners have email addresses.
            </p>

            <div class="toggle-row">
              <div>
                <strong>Status:</strong>
                <span class="chip" [class.inactive]="!settingsForm.get('autoReminderEnabled')?.value">
                  {{ settingsForm.get('autoReminderEnabled')?.value ? 'Running' : 'Stopped' }}
                </span>
              </div>
              <button mat-raised-button type="button"
                      [color]="settingsForm.get('autoReminderEnabled')?.value ? 'warn' : 'primary'"
                      (click)="toggleAndSave('autoReminderEnabled')">
                <mat-icon>{{ settingsForm.get('autoReminderEnabled')?.value ? 'stop' : 'play_arrow' }}</mat-icon>
                {{ settingsForm.get('autoReminderEnabled')?.value ? 'Stop Reminders' : 'Start Reminders' }}
              </button>
            </div>

            <div class="form-row">
              <mat-form-field>
                <mat-label>Reminder Day of Month</mat-label>
                <mat-select formControlName="reminderDayOfMonth">
                  <mat-option [value]="0">Last day of month</mat-option>
                  <mat-option *ngFor="let d of daysOfMonth" [value]="d">{{ ordinal(d) }}</mat-option>
                </mat-select>
                <mat-hint>Reminder emails are sent on this date every month</mat-hint>
              </mat-form-field>
            </div>

            <div class="reminder-actions">
              <button mat-stroked-button color="primary" type="button" (click)="previewReminders()">
                <mat-icon>visibility</mat-icon> Preview Recipients
              </button>
              <button mat-stroked-button color="accent" type="button" (click)="sendRemindersNow()"
                      [disabled]="sendingReminders">
                <mat-icon>send</mat-icon> {{ sendingReminders ? 'Sending...' : 'Send Reminders Now' }}
              </button>
            </div>

            <div *ngIf="reminderPreview.length > 0" class="reminder-preview">
              <p class="gateway-hint">
                {{ reminderPreview.length }} unit(s) with outstanding dues.
                {{ reminderMissingEmail }} missing an email (won't be reminded).
              </p>
              <div class="preview-row" *ngFor="let r of reminderPreview">
                <span class="unit">{{ r.unitNumber }}</span>
                <span class="owner">{{ r.ownerName }}</span>
                <span class="email" [class.missing]="!r.email">{{ r.email || 'no email' }}</span>
                <span class="amount">&#8377; {{ r.outstanding }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Visitor / Gate Approval -->
        <mat-card>
          <mat-card-header>
            <mat-card-title>Visitor / Gate Approval</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="gateway-hint">
              When a security guard sends a delivery/visitor to a flat for approval, the request
              auto-expires if the owner doesn't respond within this many minutes.
            </p>
            <div class="form-row">
              <mat-form-field>
                <mat-label>Approval Timeout (minutes)</mat-label>
                <input matInput type="number" formControlName="visitorApprovalTimeoutMinutes" min="1" max="120">
                <mat-hint>How long a request stays pending (1–120)</mat-hint>
                <mat-error *ngIf="settingsForm.get('visitorApprovalTimeoutMinutes')?.invalid">
                  Enter a value between 1 and 120
                </mat-error>
              </mat-form-field>
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

    .toggle-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 16px; }
    .reminder-actions { display: flex; gap: 12px; margin-top: 8px; flex-wrap: wrap; }
    .reminder-preview { margin-top: 16px; border-top: 1px solid #eee; padding-top: 12px; }
    .preview-row { display: flex; gap: 12px; align-items: center; padding: 6px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
    .preview-row .unit { font-weight: bold; min-width: 70px; }
    .preview-row .owner { flex: 1; color: #555; }
    .preview-row .email { color: #777; min-width: 180px; }
    .preview-row .email.missing { color: #c62828; font-style: italic; }
    .preview-row .amount { font-weight: bold; color: #e65100; min-width: 90px; text-align: right; }
  `]
})
export class SettingsComponent implements OnInit {
  settingsForm: FormGroup;

  // NOC Types management
  types: any[] = [];
  showAddForm = false;
  newType: any = { code: '', name: '', description: '', defaultTemplate: '', displayOrder: 0, isActive: true };

  // Payment reminder preview/send state
  reminderPreview: Array<{ unitNumber: string; ownerName: string; email: string | null; outstanding: string }> = [];
  reminderMissingEmail = 0;
  sendingReminders = false;

  // Day-of-month options (1..31) for the bill due / generation / reminder date pickers.
  // The value 0 (a separate option) means "last day of the month".
  daysOfMonth: number[] = Array.from({ length: 31 }, (_, i) => i + 1);

  /** 1 -> "1st", 2 -> "2nd", 23 -> "23rd" for friendly day labels. */
  ordinal(n: number): string {
    const s = ['th', 'st', 'nd', 'rd'];
    const v = n % 100;
    return n + (s[(v - 20) % 10] || s[v] || s[0]);
  }

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
      discountMessage: ['Pay online before the due date and get a discount!'],
      visitorApprovalTimeoutMinutes: [10, [Validators.min(1), Validators.max(120)]],
      // Maintenance bill settings
      lateFeeAmount: [0, [Validators.min(0)]],
      billDueDay: [0, [Validators.min(0), Validators.max(31)]],
      // Automated bill generation
      autoBillGenerationEnabled: [false],
      billGenerationDay: [1, [Validators.min(1), Validators.max(31)]],
      // Automated payment reminders
      autoReminderEnabled: [false],
      reminderDayOfMonth: [5, [Validators.min(1), Validators.max(31)]]
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

  /**
   * Flip an automation toggle (start/stop) and immediately persist. The backend PUT
   * replaces the whole settings row, so we send the full form value (all fields are
   * registered as controls) to avoid nulling anything.
   */
  toggleAndSave(controlName: 'autoBillGenerationEnabled' | 'autoReminderEnabled'): void {
    const control = this.settingsForm.get(controlName);
    if (!control) { return; }
    const newValue = !control.value;
    control.setValue(newValue);

    this.http.put<{ success: boolean }>(`${environment.apiUrl}/settings`, this.settingsForm.value).subscribe({
      next: () => {
        const label = controlName === 'autoBillGenerationEnabled' ? 'Bill generation' : 'Payment reminders';
        this.snackBar.open(`${label} ${newValue ? 'started' : 'stopped'}`, 'Close', { duration: 3000 });
      },
      error: () => {
        // Revert the toggle on failure so the UI reflects the real state.
        control.setValue(!newValue);
        this.snackBar.open('Failed to update automation', 'Close', { duration: 3000 });
      }
    });
  }

  previewReminders(): void {
    this.http.get<{ success: boolean; data: any[] }>(`${environment.apiUrl}/maintenance/reminders/preview`).subscribe({
      next: (res) => {
        if (res.success) {
          this.reminderPreview = (res.data || []).map(r => ({
            unitNumber: r.unitNumber,
            ownerName: r.ownerName,
            email: r.email,
            outstanding: r.outstanding
          }));
          this.reminderMissingEmail = this.reminderPreview.filter(r => !r.email).length;
          if (this.reminderPreview.length === 0) {
            this.snackBar.open('No units have outstanding dues right now', 'Close', { duration: 3000 });
          }
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to load reminder preview', 'Close', { duration: 4000 })
    });
  }

  sendRemindersNow(): void {
    this.sendingReminders = true;
    this.http.post<{ success: boolean; data: any }>(`${environment.apiUrl}/maintenance/reminders/send`, {}).subscribe({
      next: (res) => {
        this.sendingReminders = false;
        if (res.success) {
          const d = res.data;
          if (!d.mailConfigured) {
            this.snackBar.open('Email is not configured on the server. No reminders sent.', 'Close', { duration: 5000 });
          } else {
            this.snackBar.open(`Sent ${d.sent} reminder(s). ${d.skippedNoEmail} skipped (no email).`, 'Close', { duration: 5000 });
          }
        }
      },
      error: (err) => {
        this.sendingReminders = false;
        this.snackBar.open(err.error?.message || 'Failed to send reminders', 'Close', { duration: 4000 });
      }
    });
  }
}
