import { groupBySection, NavItem, visibleNavItems } from './navigation';

describe('navigation', () => {
  const items: NavItem[] = [
    { label: 'Dashboard', icon: 'x', route: '/d', section: 'Self-service', roles: ['EMPLOYEE', 'ERP_ADMIN'], available: true },
    { label: 'Approvals', icon: 'x', route: '/a', section: 'Team', roles: ['SUPERVISOR'], available: true },
    { label: 'Audit', icon: 'x', route: '/au', section: 'Administration', roles: ['ERP_ADMIN'], available: true },
    { label: 'Travel', icon: 'x', route: '/t', section: 'Self-service', roles: ['EMPLOYEE'], available: false },
  ];

  it('shows only items matching one of the user roles', () => {
    expect(visibleNavItems(['EMPLOYEE'], items).map((i) => i.label)).toEqual(['Dashboard']);
    expect(visibleNavItems(['EMPLOYEE', 'SUPERVISOR'], items).map((i) => i.label)).toEqual([
      'Dashboard',
      'Approvals',
    ]);
  });

  it('hides items whose phase is not delivered yet', () => {
    expect(visibleNavItems(['EMPLOYEE'], items).some((i) => i.label === 'Travel')).toBe(false);
  });

  it('gives an ERP admin without the EMPLOYEE role the administration menu', () => {
    const groups = groupBySection(visibleNavItems(['ERP_ADMIN'], items));
    expect(groups.map((g) => g.section)).toEqual(['Self-service', 'Administration']);
  });
});
