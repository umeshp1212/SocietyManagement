import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TenantService } from '@core/services/tenant.service';
import { OwnerService } from '@core/services/owner.service';
import { Unit } from '@core/models/owner.model';

@Component({
  selector: 'app-tenant-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatDatepickerModule, MatNativeDateModule, MatIconModule,
    MatDividerModule, MatSnackBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Update Tenant' : 'Register New Tenant' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="tenantForm" (ngSubmit)="onSubmit()">

            <!-- Unit Selection (only for new registration) -->
            <h4>Unit Details</h4>
            <div class="form-row" *ngIf="!isEdit">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Select Unit (Flat/Shop) *</mat-label>
                <mat-select formControlName="unitId">
                  <mat-option *ngFor="let unit of units" [value]="unit.unitId">
                    {{ unit.unitNumber }} ({{ unit.unitType }}) - {{ unit.primaryOwnerName || 'No owner' }}
                  </mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <!-- Tenant Information -->
            <h4>Tenant Information</h4>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Tenant Name *</mat-label>
                <input matInput formControlName="tenantName">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Contact Number *</mat-label>
                <input matInput formControlName="contactNumber">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="email">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Aadhar Number</mat-label>
                <input matInput formControlName="aadharNumber">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>PAN Number</mat-label>
                <input matInput formControlName="panNumber">
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Permanent Address</mat-label>
              <textarea matInput formControlName="permanentAddress" rows="2"></textarea>
            </mat-form-field>

            <!-- Rental Details -->
            <h4>Rental Details</h4>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Rent Start Date *</mat-label>
                <input matInput [matDatepicker]="startPicker" formControlName="rentStartDate">
                <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
                <mat-datepicker #startPicker></mat-datepicker>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Rent End Date</mat-label>
                <input matInput [matDatepicker]="endPicker" formControlName="rentEndDate">
                <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
                <mat-datepicker #endPicker></mat-datepicker>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Monthly Rent (Rs)</mat-label>
                <input matInput type="number" formControlName="monthlyRentAmount">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Security Deposit (Rs)</mat-label>
                <input matInput type="number" formControlName="securityDeposit">
              </mat-form-field>
            </div>

            <!-- Family Members -->
            <mat-divider></mat-divider>
            <div class="section-header">
              <h4>Family Members</h4>
              <button mat-mini-fab color="primary" type="button" (click)="addFamilyMember()">
                <mat-icon>add</mat-icon>
              </button>
            </div>

            <div formArrayName="familyMembers">
              <div *ngFor="let member of familyMembers.controls; let i = index"
                   [formGroupName]="i" class="dynamic-row">
                <div class="form-row">
                  <mat-form-field appearance="outline">
                    <mat-label>Name *</mat-label>
                    <input matInput formControlName="memberName">
                  </mat-form-field>
                  <mat-form-field appearance="outline" style="max-width: 100px;">
                    <mat-label>Age</mat-label>
                    <input matInput type="number" formControlName="age">
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Relation *</mat-label>
                    <mat-select formControlName="relation">
                      <mat-option value="Spouse">Spouse</mat-option>
                      <mat-option value="Son">Son</mat-option>
                      <mat-option value="Daughter">Daughter</mat-option>
                      <mat-option value="Father">Father</mat-option>
                      <mat-option value="Mother">Mother</mat-option>
                      <mat-option value="Brother">Brother</mat-option>
                      <mat-option value="Sister">Sister</mat-option>
                      <mat-option value="Other">Other</mat-option>
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Contact</mat-label>
                    <input matInput formControlName="contactNumber">
                  </mat-form-field>
                  <button mat-icon-button color="warn" type="button" (click)="removeFamilyMember(i)">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              </div>
            </div>

            <!-- Vehicles -->
            <mat-divider></mat-divider>
            <div class="section-header">
              <h4>Vehicles</h4>
              <button mat-mini-fab color="primary" type="button" (click)="addVehicle()">
                <mat-icon>add</mat-icon>
              </button>
            </div>

            <div formArrayName="vehicles">
              <div *ngFor="let vehicle of vehicles.controls; let i = index"
                   [formGroupName]="i" class="dynamic-row">
                <div class="form-row">
                  <mat-form-field appearance="outline">
                    <mat-label>Vehicle Type *</mat-label>
                    <mat-select formControlName="vehicleType">
                      <mat-option value="Two Wheeler">Two Wheeler</mat-option>
                      <mat-option value="Four Wheeler">Four Wheeler</mat-option>
                      <mat-option value="Three Wheeler">Three Wheeler</mat-option>
                      <mat-option value="Bicycle">Bicycle</mat-option>
                      <mat-option value="Other">Other</mat-option>
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Vehicle Number *</mat-label>
                    <input matInput formControlName="vehicleNumber" placeholder="MH 12 AB 1234">
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Parking Slot</mat-label>
                    <input matInput formControlName="parkingSlot">
                  </mat-form-field>
                  <button mat-icon-button color="warn" type="button" (click)="removeVehicle(i)">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              </div>
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/tenants">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="tenantForm.invalid">
                {{ isEdit ? 'Update Tenant' : 'Register Tenant' }}
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
    .section-header { display: flex; justify-content: space-between; align-items: center; }
    .section-header h4 { flex: 1; }
    .dynamic-row { background: #fafafa; border-radius: 8px; padding: 8px 12px; margin-bottom: 8px; }
    .dynamic-row .form-row { margin-bottom: 0; align-items: center; }
  `]
})
export class TenantFormComponent implements OnInit {
  tenantForm!: FormGroup;
  isEdit = false;
  tenantId?: number;
  units: Unit[] = [];

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private ownerService: OwnerService,
    private route: ActivatedRoute,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.tenantForm = this.fb.group({
      unitId: [null, Validators.required],
      tenantName: ['', [Validators.required, Validators.maxLength(150)]],
      contactNumber: ['', [Validators.required, Validators.maxLength(15)]],
      email: ['', Validators.email],
      aadharNumber: [''],
      panNumber: [''],
      permanentAddress: [''],
      rentStartDate: [null, Validators.required],
      rentEndDate: [null],
      monthlyRentAmount: [null],
      securityDeposit: [null],
      familyMembers: this.fb.array([]),
      vehicles: this.fb.array([])
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.tenantId = +id;
      this.tenantForm.get('unitId')?.clearValidators();
      this.loadTenant();
    } else {
      this.loadUnits();
    }
  }

  get familyMembers(): FormArray {
    return this.tenantForm.get('familyMembers') as FormArray;
  }

  get vehicles(): FormArray {
    return this.tenantForm.get('vehicles') as FormArray;
  }

  loadUnits(): void {
    // Load units that are not already rented (self-occupied or vacant with owners)
    this.ownerService.getAllUnits(0, 200).subscribe(res => {
      if (res.success) {
        this.units = res.data.content.filter(u =>
          u.occupancyStatus === 'SELF_OCCUPIED' || u.occupancyStatus === 'VACANT'
        );
      }
    });
  }

  loadTenant(): void {
    this.tenantService.getTenantById(this.tenantId!).subscribe(res => {
      if (res.success) {
        const t = res.data;
        this.tenantForm.patchValue({
          unitId: t.unitId,
          tenantName: t.tenantName,
          contactNumber: t.contactNumber,
          email: t.email,
          aadharNumber: t.aadharNumber,
          panNumber: t.panNumber,
          permanentAddress: t.permanentAddress,
          rentStartDate: t.rentStartDate ? new Date(t.rentStartDate) : null,
          rentEndDate: t.rentEndDate ? new Date(t.rentEndDate) : null,
          monthlyRentAmount: t.monthlyRentAmount,
          securityDeposit: t.securityDeposit
        });

        // Load family members
        if (t.familyMembers) {
          t.familyMembers.forEach(fm => {
            this.familyMembers.push(this.fb.group({
              memberName: [fm.memberName, Validators.required],
              age: [fm.age],
              relation: [fm.relation, Validators.required],
              aadharNumber: [fm.aadharNumber],
              contactNumber: [fm.contactNumber]
            }));
          });
        }

        // Load vehicles
        if (t.vehicles) {
          t.vehicles.forEach(v => {
            this.vehicles.push(this.fb.group({
              vehicleType: [v.vehicleType, Validators.required],
              vehicleNumber: [v.vehicleNumber, Validators.required],
              parkingSlot: [v.parkingSlot]
            }));
          });
        }
      }
    });
  }

  addFamilyMember(): void {
    this.familyMembers.push(this.fb.group({
      memberName: ['', Validators.required],
      age: [null],
      relation: ['', Validators.required],
      aadharNumber: [''],
      contactNumber: ['']
    }));
  }

  removeFamilyMember(index: number): void {
    this.familyMembers.removeAt(index);
  }

  addVehicle(): void {
    this.vehicles.push(this.fb.group({
      vehicleType: ['', Validators.required],
      vehicleNumber: ['', Validators.required],
      parkingSlot: ['']
    }));
  }

  removeVehicle(index: number): void {
    this.vehicles.removeAt(index);
  }

  onSubmit(): void {
    if (this.tenantForm.invalid) return;

    const formValue = this.tenantForm.value;
    const request = {
      ...formValue,
      rentStartDate: formValue.rentStartDate ? this.formatDate(formValue.rentStartDate) : null,
      rentEndDate: formValue.rentEndDate ? this.formatDate(formValue.rentEndDate) : null
    };

    if (this.isEdit) {
      // Remove unitId for update (not changeable)
      const { unitId, ...updateRequest } = request;
      this.tenantService.updateTenant(this.tenantId!, updateRequest).subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Tenant updated successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/tenants']);
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Update failed', 'Close', { duration: 5000 })
      });
    } else {
      this.tenantService.registerTenant(request).subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Tenant registered successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/tenants']);
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Registration failed', 'Close', { duration: 5000 })
      });
    }
  }

  private formatDate(date: Date): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toISOString().split('T')[0];
  }
}
