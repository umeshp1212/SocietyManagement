import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OwnerService } from '@core/services/owner.service';
import { Owner, Unit } from '@core/models/owner.model';

@Component({
  selector: 'app-owner-transfer',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatDatepickerModule, MatNativeDateModule, MatSnackBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Transfer Ownership</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="transferForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Select Unit *</mat-label>
              <mat-select formControlName="unitId">
                <mat-option *ngFor="let unit of units" [value]="unit.unitId">
                  {{ unit.unitNumber }} - {{ unit.primaryOwnerName || 'No owner' }}
                </mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New Owner *</mat-label>
              <mat-select formControlName="newOwnerId">
                <mat-option *ngFor="let owner of activeOwners" [value]="owner.ownerId">
                  {{ owner.fullName }} ({{ owner.contactNumber }})
                </mat-option>
              </mat-select>
            </mat-form-field>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Transfer Date *</mat-label>
                <input matInput [matDatepicker]="picker" formControlName="transferDate">
                <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
                <mat-datepicker #picker></mat-datepicker>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Transfer Type *</mat-label>
                <mat-select formControlName="transferType">
                  <mat-option value="PURCHASE">Purchase</mat-option>
                  <mat-option value="INHERITANCE">Inheritance</mat-option>
                  <mat-option value="GIFT">Gift</mat-option>
                  <mat-option value="COURT_ORDER">Court Order</mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Remarks</mat-label>
              <textarea matInput formControlName="remarks" rows="3"></textarea>
            </mat-form-field>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/owners">Cancel</button>
              <button mat-raised-button color="warn" type="submit"
                      [disabled]="transferForm.invalid">
                Transfer Ownership
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`.full-width { width: 100%; }`]
})
export class OwnerTransferComponent implements OnInit {
  transferForm!: FormGroup;
  units: Unit[] = [];
  activeOwners: Owner[] = [];

  constructor(
    private fb: FormBuilder,
    private ownerService: OwnerService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.transferForm = this.fb.group({
      unitId: [null, Validators.required],
      newOwnerId: [null, Validators.required],
      transferDate: [null, Validators.required],
      transferType: ['PURCHASE', Validators.required],
      remarks: ['']
    });

    this.ownerService.getAllUnits(0, 200).subscribe(res => {
      if (res.success) this.units = res.data.content;
    });

    this.ownerService.getActiveOwnersList().subscribe(res => {
      if (res.success) this.activeOwners = res.data;
    });
  }

  onSubmit(): void {
    if (this.transferForm.invalid) return;

    const formValue = this.transferForm.value;
    const request = {
      ...formValue,
      transferDate: this.formatDate(formValue.transferDate)
    };

    this.ownerService.transferOwnership(request).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Ownership transferred successfully', 'Close', { duration: 3000 });
          this.router.navigate(['/owners']);
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Transfer failed', 'Close', { duration: 5000 });
      }
    });
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }
}
