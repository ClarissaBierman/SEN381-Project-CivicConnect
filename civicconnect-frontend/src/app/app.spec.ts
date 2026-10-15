import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { SessionService } from './core/session.service';

describe('App shell', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('shows no menu when nobody is signed in', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('.topbar')).toBeNull();
  });

  // Screen map (PED 22.1): the menu only shows the current role's screens.
  it('shows only requester links to a requester', async () => {
    TestBed.inject(SessionService).signInAs('REQUESTER');
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual(['My Requests', 'New Request', 'Profile']);
  });

  it('shows the management menu to a manager', async () => {
    TestBed.inject(SessionService).signInAs('MANAGEMENT');
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual(['Activity', 'Requests', 'Categories', 'Staff', 'Audit', 'My account']);
  });
});
