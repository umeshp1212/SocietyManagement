import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

interface BulkUploadResult {
  totalRecords: number;
  successCount: number;
  failedCount: number;
  errors: string[];
  successMessages: string[];
}

@Component({
  selector: 'app-owner-bulk-upload',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, MatSnackBarModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Bulk Upload Owners</h2>
        <a mat-button routerLink="/owners">Back to Owners</a>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Upload Owners via CSV</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <!-- Instructions -->
          <div class="instructions">
            <h4>Instructions:</h4>
            <ol>
              <li>Download the CSV template below</li>
              <li>Fill in owner details (one row per owner-unit mapping)</li>
              <li>For co-owners, add multiple rows for the same unit with different owners</li>
              <li>Set <code>is_primary</code> to <code>true</code> for the primary owner</li>
              <li>Set <code>ownership_percentage</code> for each co-owner (should total 100%)</li>
              <li>Upload the filled CSV file</li>
            </ol>
            <p><strong>Note:</strong> Units must already exist in the system. If an owner with the same contact number exists, they will be linked to the unit (not duplicated).</p>
          </div>

          <!-- Template Download -->
          <div class="template-section">
            <button mat-raised-button color="accent" (click)="downloadTemplate()">
              <mat-icon>download</mat-icon> Download CSV Template
            </button>
          </div>

          <!-- File Upload -->
          <div class="upload-section">
            <input type="file" #fileInput (change)="onFileSelected($event)"
                   accept=".csv" style="display:none">
            <button mat-raised-button color="primary" (click)="fileInput.click()"
                    [disabled]="uploading">
              <mat-icon>cloud_upload</mat-icon> Select CSV File
            </button>
            <span class="file-name" *ngIf="selectedFile">{{ selectedFile.name }}</span>
          </div>

          <div *ngIf="selectedFile" class="upload-action">
            <button mat-raised-button color="primary" (click)="uploadFile()"
                    [disabled]="uploading">
              <mat-icon>upload_file</mat-icon> Upload & Process
            </button>
          </div>

          <!-- Progress -->
          <mat-progress-bar *ngIf="uploading" mode="indeterminate"></mat-progress-bar>

          <!-- Results -->
          <div *ngIf="result" class="results-section">
            <h4>Upload Results</h4>
            <div class="result-summary">
              <div class="result-item success">
                <mat-icon>check_circle</mat-icon>
                <span>{{ result.successCount }} Successful</span>
              </div>
              <div class="result-item error">
                <mat-icon>error</mat-icon>
                <span>{{ result.failedCount }} Failed</span>
              </div>
              <div class="result-item info">
                <mat-icon>info</mat-icon>
                <span>{{ result.totalRecords }} Total Records</span>
              </div>
            </div>

            <!-- Errors -->
            <div *ngIf="result.errors.length > 0" class="error-list">
              <h5>Errors:</h5>
              <div *ngFor="let error of result.errors" class="error-item">
                <mat-icon>warning</mat-icon> {{ error }}
              </div>
            </div>

            <!-- Success Messages -->
            <div *ngIf="result.successMessages.length > 0 && result.successMessages.length <= 20"
                 class="success-list">
              <h5>Processed:</h5>
              <div *ngFor="let msg of result.successMessages" class="success-item">
                <mat-icon>check</mat-icon> {{ msg }}
              </div>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- CSV Format Reference -->
      <mat-card style="margin-top: 16px">
        <mat-card-header>
          <mat-card-title>CSV Format Reference</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="csv-format">
            <table class="format-table">
              <tr><th>Column</th><th>Required</th><th>Description</th></tr>
              <tr><td>unit_number</td><td>Yes</td><td>Flat/Shop number (e.g., A-101, S-01)</td></tr>
              <tr><td>full_name</td><td>Yes</td><td>Owner full name</td></tr>
              <tr><td>contact_number</td><td>Yes</td><td>Primary phone number</td></tr>
              <tr><td>alternate_number</td><td>No</td><td>Secondary phone</td></tr>
              <tr><td>email</td><td>No</td><td>Email address</td></tr>
              <tr><td>aadhar_number</td><td>No</td><td>Aadhar card number</td></tr>
              <tr><td>pan_number</td><td>No</td><td>PAN card number</td></tr>
              <tr><td>permanent_address</td><td>No</td><td>Address</td></tr>
              <tr><td>occupation</td><td>No</td><td>Occupation/Profession</td></tr>
              <tr><td>emergency_contact_name</td><td>No</td><td>Emergency contact name</td></tr>
              <tr><td>emergency_contact_phone</td><td>No</td><td>Emergency contact phone</td></tr>
              <tr><td>is_primary</td><td>No</td><td>true/false (first owner auto-set as primary)</td></tr>
              <tr><td>ownership_percentage</td><td>No</td><td>Ownership % (default: 100)</td></tr>
            </table>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .instructions { background: #e3f2fd; padding: 16px; border-radius: 8px; margin-bottom: 20px; }
    .instructions h4 { margin: 0 0 8px; color: #1565c0; }
    .instructions ol { margin: 0; padding-left: 20px; }
    .instructions li { margin: 4px 0; }
    .instructions code { background: #fff; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
    .template-section { margin-bottom: 20px; }
    .upload-section { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    .upload-action { margin-bottom: 16px; }
    .file-name { font-weight: 500; color: #1976d2; }
    .results-section { margin-top: 20px; padding: 16px; background: #fafafa; border-radius: 8px; }
    .results-section h4 { margin: 0 0 12px; }
    .result-summary { display: flex; gap: 24px; margin-bottom: 16px; }
    .result-item { display: flex; align-items: center; gap: 6px; font-weight: 500; }
    .result-item.success { color: #2e7d32; }
    .result-item.error { color: #c62828; }
    .result-item.info { color: #1565c0; }
    .error-list, .success-list { margin-top: 12px; max-height: 300px; overflow-y: auto; }
    .error-list h5, .success-list h5 { margin: 0 0 8px; }
    .error-item { display: flex; align-items: center; gap: 6px; color: #c62828; font-size: 13px; padding: 4px 0; }
    .error-item mat-icon { font-size: 16px; height: 16px; width: 16px; }
    .success-item { display: flex; align-items: center; gap: 6px; color: #2e7d32; font-size: 13px; padding: 2px 0; }
    .success-item mat-icon { font-size: 16px; height: 16px; width: 16px; }
    .format-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .format-table th, .format-table td { border: 1px solid #e0e0e0; padding: 8px 12px; text-align: left; }
    .format-table th { background: #f5f5f5; font-weight: 500; }
    .csv-format { overflow-x: auto; }
  `]
})
export class OwnerBulkUploadComponent {
  selectedFile: File | null = null;
  uploading = false;
  result: BulkUploadResult | null = null;

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  downloadTemplate(): void {
    this.http.get(`${this.apiUrl}/bulk-upload/templates/owners`, { responseType: 'blob' })
      .subscribe(blob => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'owners_template.csv';
        link.click();
        window.URL.revokeObjectURL(url);
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      this.result = null;
    }
  }

  uploadFile(): void {
    if (!this.selectedFile) return;

    this.uploading = true;
    this.result = null;

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    this.http.post<any>(`${this.apiUrl}/bulk-upload/owners`, formData).subscribe({
      next: (res) => {
        this.uploading = false;
        if (res.success) {
          this.result = res.data;
          this.snackBar.open(res.message, 'Close', { duration: 5000 });
        }
      },
      error: (err) => {
        this.uploading = false;
        this.snackBar.open(err.error?.message || 'Upload failed', 'Close', { duration: 5000 });
      }
    });
  }
}
