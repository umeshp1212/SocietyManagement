import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TdsRemittanceStatus } from '@core/models/tds.model';

@Component({
  selector: 'app-tds-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="status-badge" [ngClass]="status.toLowerCase()">{{ label }}</span>
  `,
  styles: [`
    .status-badge {
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 0.8em;
      font-weight: 500;
      white-space: nowrap;
    }
    .status-badge.deducted { background: #eeeeee; color: #616161; }
    .status-badge.paid_to_accountant { background: #ffecb3; color: #f57c00; }
    .status-badge.paid_to_it_department { background: #c8e6c9; color: #388e3c; }
  `]
})
export class TdsStatusBadgeComponent {
  @Input() status!: TdsRemittanceStatus;

  private static readonly LABELS: Record<TdsRemittanceStatus, string> = {
    DEDUCTED: 'Deducted',
    PAID_TO_ACCOUNTANT: 'Paid to Accountant',
    PAID_TO_IT_DEPARTMENT: 'Paid to IT Dept'
  };

  get label(): string {
    return TdsStatusBadgeComponent.LABELS[this.status] ?? this.status;
  }
}
