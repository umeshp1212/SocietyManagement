import { CommonModule, Location } from '@angular/common';
import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/**
 * Shared back-navigation button.
 *
 * <p>Provides a single, consistently themed "back" control for internal pages
 * (detail views, forms, sub-pages). It renders as a Material text button
 * ({@code mat-button}) with a leading {@code arrow_back} icon, so every page uses
 * the same look instead of the ad-hoc mix of {@code mat-button}/{@code mat-raised-button},
 * icon/no-icon variants that existed before.
 *
 * <p>Navigation behaviour:
 * <ul>
 *   <li>If {@code link} is provided, it navigates to that route (absolute path).</li>
 *   <li>Otherwise it falls back to {@code Location.back()} (browser history).</li>
 * </ul>
 *
 * Usage:
 * ```html
 * <!-- history back -->
 * <app-back-button></app-back-button>
 *
 * <!-- explicit destination + custom label -->
 * <app-back-button link="/owners" label="Back to Owners"></app-back-button>
 * ```
 */
@Component({
  selector: 'app-back-button',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  template: `
    <button mat-button type="button" class="back-button" (click)="goBack()"
            [attr.aria-label]="label">
      <mat-icon>arrow_back</mat-icon>
      <span>{{ label }}</span>
    </button>
  `,
  styles: [`
    .back-button mat-icon {
      margin-right: 4px;
    }
  `]
})
export class BackButtonComponent {
  /** Button text. Defaults to "Back". */
  @Input() label = 'Back';

  /** Optional explicit destination route. When omitted, uses browser history. */
  @Input() link?: string;

  constructor(private location: Location, private router: Router) {}

  goBack(): void {
    if (this.link) {
      this.router.navigateByUrl(this.link);
    } else {
      this.location.back();
    }
  }
}
