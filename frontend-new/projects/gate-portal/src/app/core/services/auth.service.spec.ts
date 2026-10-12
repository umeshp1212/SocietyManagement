import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';

/**
 * Tests for guard auth (plan Task 8): isGuardLoggedIn() accepts a non-expired token
 * carrying the SECURITY role and rejects a token without it. No real HTTP/router needed.
 */
describe('AuthService (gate)', () => {
  let service: AuthService;

  /** Builds a fake unsigned JWT (header.payload.signature) with the given claims. */
  function fakeJwt(claims: object): string {
    const b64 = (o: object) => btoa(JSON.stringify(o));
    return `${b64({ alg: 'none' })}.${b64(claims)}.sig`;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: HttpClient, useValue: jasmine.createSpyObj('HttpClient', ['post', 'get']) },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) }
      ]
    });
    service = TestBed.inject(AuthService);
    localStorage.clear();
  });

  afterEach(() => localStorage.clear());

  it('returns false when no token is present', () => {
    expect(service.isGuardLoggedIn()).toBeFalse();
  });

  it('accepts a non-expired SECURITY token', () => {
    const token = fakeJwt({ exp: Math.floor(Date.now() / 1000) + 3600, roles: ['SECURITY'] });
    localStorage.setItem('gate_access_token', token);
    expect(service.isGuardLoggedIn()).toBeTrue();
  });

  it('rejects a token without SECURITY/SUPER_ADMIN role', () => {
    const token = fakeJwt({ exp: Math.floor(Date.now() / 1000) + 3600, roles: ['OWNER'] });
    localStorage.setItem('gate_access_token', token);
    expect(service.isGuardLoggedIn()).toBeFalse();
  });

  it('rejects an expired SECURITY token', () => {
    const token = fakeJwt({ exp: Math.floor(Date.now() / 1000) - 10, roles: ['SECURITY'] });
    localStorage.setItem('gate_access_token', token);
    expect(service.isGuardLoggedIn()).toBeFalse();
  });
});
