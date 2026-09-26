-- DEMO DATA ONLY (ADR-009). Annual-leave entitlements for 2026 and 2027 and one
-- example delegation. Leave requests themselves are created by the demo seeder
-- through the application services, so they always pass the real business rules.

-- 30 days for a five-day week, pro rata for fewer working days:
-- 'parttime' (09) works 4 days/week -> 24 days. 'nhalbtag' (11) works 5 short days -> 30 days.
INSERT INTO leave_entitlement (id, employee_id, year, leave_type_id, base_days, carry_over_days,
                               additional_days, expiry_date)
SELECT md5(e.id::text || y.year || 'ANNUAL_LEAVE')::uuid,
       e.id,
       y.year,
       '00000000-0000-0000-0000-000000000301',
       CASE right(e.id::text, 2) WHEN '09' THEN 24 ELSE 30 END,
       CASE WHEN y.year = 2026 AND right(e.id::text, 2) IN ('01', '12', '14') THEN 3 ELSE 0 END,
       CASE WHEN right(e.id::text, 2) = '14' THEN 2 ELSE 0 END,
       make_date(y.year, 3, 31)
FROM employee e
CROSS JOIN (VALUES (2026), (2027)) AS y (year)
WHERE e.id::text LIKE '20000000-0000-0000-0000-0000000000__'
ON CONFLICT (employee_id, year, leave_type_id) DO NOTHING;

-- 'supervisor' (02) will be away and delegates absence approvals to 'supervisor2' (10).
INSERT INTO delegation (id, delegator_id, delegate_id, approval_type, valid_from, valid_to, active, created_at)
VALUES ('00000000-0000-0000-0000-000000000401',
        '20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000010',
        'ABSENCE', DATE '2026-12-21', DATE '2027-01-03', TRUE, now())
ON CONFLICT (id) DO NOTHING;
