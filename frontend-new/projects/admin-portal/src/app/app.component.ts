import { Component } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs';
import { LayoutComponent } from './core/layout/layout.component';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, LayoutComponent],
  template: `
    <router-outlet *ngIf="isPublicPage"></router-outlet>
    <app-layout *ngIf="!isPublicPage">
      <router-outlet></router-outlet>
    </app-layout>
  `
})
export class AppComponent {
  title = 'Society Management';
  // When true, the route renders standalone (no admin dashboard chrome).
  // Covers auth screens and the public, pre-login website pages.
  isPublicPage = false;

  constructor(private router: Router, private authService: AuthService) {
    // Refresh cached roles/permissions on startup so grants made in the
    // Role & Permission module apply on next load without a full re-login.
    if (this.authService.isLoggedIn()) {
      this.authService.refreshCurrentUser().subscribe({ error: () => {} });
    }

    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      // Routes that must render WITHOUT the admin layout (public website + auth).
      const publicPages = [
        '/login', '/forgot-password', '/reset-password',
        '/products', '/contact', '/terms', '/refunds', '/privacy'
      ];
      this.isPublicPage = event.url === '/'
        || publicPages.some(page => event.url === page || event.url.startsWith(page + '?'));
    });
  }
}
