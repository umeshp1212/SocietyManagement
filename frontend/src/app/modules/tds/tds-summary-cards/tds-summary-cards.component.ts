import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { TdsSummaryDTO } from '@core/models/tds.model';

/**
 * Presentational cards for the TDS dashboard.
 *
 * Displays four monetary totals (Total Deducted, Paid to Accountant,
 * Paid to IT Dept, Pending) plus an optional line count. The parent
 * component fetches the summary from `/tds/summary` on every filter
 * change and passes the new value in via the `summary` input, so the
 * cards recompute/display on filter change (Requirement 10.5).
 *
 * When `summary` is null/absent (no records), every monetary card
 * renders 0.00 and the line count shows 0.
 */
@Component({
  selector: 'app-tds-summary-cards',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <div class="summary-cards">
      <mat-card class="summary-card">
        <mat-card-content>
          <div class="card-label">Total Deducted</div>
          <div class="card-value">
            &#8377; {{ (summary?.totalDeducted || 0) | number:'1.2-2' }}
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="summary-card">
        <mat-card-content>
          <div class="card-label">Paid to Accountant</div>
          <div class="card-value">
            &#8377; {{ (summary?.totalPaidToAccountant || 0) | number:'1.2-2' }}
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="summary-card">
        <mat-card-content>
          <div class="card-label">Paid to IT Dept</div>
          <div class="card-value">
            &#8377; {{ (summary?.totalPaidToItDept || 0) | number:'1.2-2' }}
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="summary-card pending-card">
        <mat-card-content>
          <div class="card-label">Pending</div>
          <div class="card-value">
            &#8377; {{ (summary?.totalPending || 0) | number:'1.2-2' }}
          </div>
        </mat-card-content>
      </mat-card>
    </div>

    <div class="line-count">
      {{ summary?.lineCount || 0 }} record{{ (summary?.lineCount || 0) === 1 ? '' : 's' }}
    </div>
  `,
  styles: [`
    .summary-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 8px;
    }
    .summary-card {
      border-left: 4px solid #1976d2;
    }
    .card-label {
      font-size: 0.85em;
      color: #666;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      margin-bottom: 8px;
    }
    .card-value {
      font-size: 1.4em;
      font-weight: 600;
      font-family: monospace;
      color: #1976d2;
    }
    .pending-card {
      border-left-color: #f57c00;
    }
    .pending-card .card-value {
      color: #f57c00;
    }
    .line-count {
      font-size: 0.85em;
      color: #666;
      margin-bottom: 16px;
    }
  `]
})
export class TdsSummaryCardsComponent {
  @Input() summary: TdsSummaryDTO | null = null;
}
