import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-vendor-detail',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `<div class="container"><mat-card><mat-card-content><p>Vendor Detail - To be implemented</p></mat-card-content></mat-card></div>`
})
export class VendorDetailComponent {}
