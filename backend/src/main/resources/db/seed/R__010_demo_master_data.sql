-- DEMO DATA ONLY (ADR-009). Loaded only when the 'demo' Spring profile adds
-- classpath:db/seed to the Flyway locations. All people, units and cost
-- centres are fictitious. Idempotent: every insert uses ON CONFLICT DO NOTHING
-- and deterministic IDs, so re-running this repeatable migration is safe.

-- ---------------------------------------------------------------- organisation
INSERT INTO organisation_unit (id, external_id, code, name, type, parent_id, cost_centre) VALUES
    ('10000000-0000-0000-0000-000000000001', 'ORG-0001', 'UNI',     'Example University',               'UNIVERSITY',             NULL,                                   'CC-1000'),
    ('10000000-0000-0000-0000-000000000002', 'ORG-0002', 'ADM',     'Central Administration',           'CENTRAL_ADMINISTRATION', '10000000-0000-0000-0000-000000000001', 'CC-1100'),
    ('10000000-0000-0000-0000-000000000003', 'ORG-0003', 'ADM-HR',  'Human Resources',                  'UNIT',                   '10000000-0000-0000-0000-000000000002', 'CC-1110'),
    ('10000000-0000-0000-0000-000000000004', 'ORG-0004', 'ADM-FIN', 'Finance',                          'UNIT',                   '10000000-0000-0000-0000-000000000002', 'CC-1120'),
    ('10000000-0000-0000-0000-000000000005', 'ORG-0005', 'ADM-IT',  'IT Services',                      'UNIT',                   '10000000-0000-0000-0000-000000000002', 'CC-1130'),
    ('10000000-0000-0000-0000-000000000006', 'ORG-0006', 'FAC-A',   'Faculty of Sciences',              'FACULTY',                '10000000-0000-0000-0000-000000000001', 'CC-2000'),
    ('10000000-0000-0000-0000-000000000007', 'ORG-0007', 'INST-A1', 'Institute of Applied Physics',     'INSTITUTE',              '10000000-0000-0000-0000-000000000006', 'CC-2100'),
    ('10000000-0000-0000-0000-000000000008', 'ORG-0008', 'INST-A2', 'Institute of Computer Science',    'INSTITUTE',              '10000000-0000-0000-0000-000000000006', 'CC-2200'),
    ('10000000-0000-0000-0000-000000000009', 'ORG-0009', 'FAC-B',   'Faculty of Humanities',            'FACULTY',                '10000000-0000-0000-0000-000000000001', 'CC-3000'),
    ('10000000-0000-0000-0000-000000000010', 'ORG-0010', 'DEPT-B1', 'Department of History',            'DEPARTMENT',             '10000000-0000-0000-0000-000000000009', 'CC-3100'),
    ('10000000-0000-0000-0000-000000000011', 'ORG-0011', 'DEPT-B2', 'Department of Linguistics',        'DEPARTMENT',             '10000000-0000-0000-0000-000000000009', 'CC-3200'),
    ('10000000-0000-0000-0000-000000000012', 'ORG-0012', 'PRJ-A2-01', 'Project: Open Research Data',    'PROJECT_UNIT',           '10000000-0000-0000-0000-000000000008', 'CC-2201')
ON CONFLICT (id) DO NOTHING;

-- -------------------------------------------------------------------- employees
INSERT INTO employee (id, external_employee_id, personnel_number, first_name, last_name, email, username, organisation_unit_id, synced_at) VALUES
    ('20000000-0000-0000-0000-000000000001', 'HR-10001', 'P10001', 'Erika',   'Mustermann', 'employee@uni.example',    'employee',    '10000000-0000-0000-0000-000000000008', now()),
    ('20000000-0000-0000-0000-000000000002', 'HR-10002', 'P10002', 'Stefan',  'Beispiel',   'supervisor@uni.example',  'supervisor',  '10000000-0000-0000-0000-000000000008', now()),
    ('20000000-0000-0000-0000-000000000003', 'HR-10003', 'P10003', 'Frieda',  'Finanz',     'finance@uni.example',     'finance',     '10000000-0000-0000-0000-000000000004', now()),
    ('20000000-0000-0000-0000-000000000004', 'HR-10004', 'P10004', 'Hanna',   'Personal',   'hradmin@uni.example',     'hradmin',     '10000000-0000-0000-0000-000000000003', now()),
    ('20000000-0000-0000-0000-000000000005', 'HR-10005', 'P10005', 'Ernst',   'Admin',      'erpadmin@uni.example',    'erpadmin',    '10000000-0000-0000-0000-000000000005', now()),
    ('20000000-0000-0000-0000-000000000006', 'HR-10006', 'P10006', 'Tim',     'Reise',      'travel@uni.example',      'travel',      '10000000-0000-0000-0000-000000000004', now()),
    ('20000000-0000-0000-0000-000000000007', 'HR-10007', 'P10007', 'Tanja',   'Zeit',       'timeadmin@uni.example',   'timeadmin',   '10000000-0000-0000-0000-000000000003', now()),
    ('20000000-0000-0000-0000-000000000008', 'HR-10008', 'P10008', 'Anton',   'Pruefer',    'auditor@uni.example',     'auditor',     '10000000-0000-0000-0000-000000000002', now()),
    ('20000000-0000-0000-0000-000000000009', 'HR-10009', 'P10009', 'Paula',   'Teilzeit',   'parttime@uni.example',    'parttime',    '10000000-0000-0000-0000-000000000008', now()),
    ('20000000-0000-0000-0000-000000000010', 'HR-10010', 'P10010', 'Bettina', 'Leitung',    'supervisor2@uni.example', 'supervisor2', '10000000-0000-0000-0000-000000000002', now()),
    ('20000000-0000-0000-0000-000000000011', 'HR-10011', 'P10011', 'Nina',    'Halbtag',    'nina.halbtag@uni.example',  'nhalbtag',  '10000000-0000-0000-0000-000000000010', now()),
    ('20000000-0000-0000-0000-000000000012', 'HR-10012', 'P10012', 'Clara',   'Beispiel',   'clara.beispiel@uni.example','cbeispiel', '10000000-0000-0000-0000-000000000008', now()),
    ('20000000-0000-0000-0000-000000000013', 'HR-10013', 'P10013', 'David',   'Muster',     'david.muster@uni.example',  'dmuster',   '10000000-0000-0000-0000-000000000012', now()),
    ('20000000-0000-0000-0000-000000000014', 'HR-10014', 'P10014', 'Emil',    'Probe',      'emil.probe@uni.example',    'eprobe',    '10000000-0000-0000-0000-000000000007', now()),
    ('20000000-0000-0000-0000-000000000015', 'HR-10015', 'P10015', 'Greta',   'Test',       'greta.test@uni.example',    'gtest',     '10000000-0000-0000-0000-000000000010', now()),
    ('20000000-0000-0000-0000-000000000016', 'HR-10016', 'P10016', 'Jan',     'Demo',       'jan.demo@uni.example',      'jdemo',     '10000000-0000-0000-0000-000000000011', now()),
    ('20000000-0000-0000-0000-000000000017', 'HR-10017', 'P10017', 'Mia',     'Fiktiv',     'mia.fiktiv@uni.example',    'mfiktiv',   '10000000-0000-0000-0000-000000000011', now()),
    ('20000000-0000-0000-0000-000000000018', 'HR-10018', 'P10018', 'Otto',    'Platzhalter','otto.platzhalter@uni.example','oplatzhalter','10000000-0000-0000-0000-000000000005', now())
ON CONFLICT (id) DO NOTHING;

-- --------------------------------------------------------------- work schedules
-- Full-time: Mon–Fri 480 min. 'parttime' (09): Mon–Thu 480, Friday off.
-- 'nhalbtag' (11): 50 %, Mon–Fri 240 min.
INSERT INTO work_schedule (id, employee_id, valid_from, valid_to, weekly_target_minutes)
SELECT ('40000000-0000-0000-0000-0000000000' || right(e.id::text, 2))::uuid,
       e.id, DATE '2024-01-01', NULL,
       CASE right(e.id::text, 2) WHEN '09' THEN 1920 WHEN '11' THEN 1200 ELSE 2400 END
FROM employee e
WHERE e.id::text LIKE '20000000-0000-0000-0000-0000000000__'
ON CONFLICT (id) DO NOTHING;

INSERT INTO work_schedule_day (id, work_schedule_id, weekday, target_minutes, working_day)
SELECT md5(ws.id::text || d.weekday)::uuid,
       ws.id,
       d.weekday,
       CASE
           WHEN d.weekday IN ('SATURDAY', 'SUNDAY') THEN 0
           WHEN right(ws.id::text, 2) = '09' AND d.weekday = 'FRIDAY' THEN 0
           WHEN right(ws.id::text, 2) = '11' THEN 240
           ELSE 480
       END,
       NOT (d.weekday IN ('SATURDAY', 'SUNDAY')
            OR (right(ws.id::text, 2) = '09' AND d.weekday = 'FRIDAY'))
FROM work_schedule ws
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
                   ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS d (weekday)
WHERE ws.id::text LIKE '40000000-0000-0000-0000-0000000000__'
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------------ employments
INSERT INTO employment (id, employee_id, external_employment_id, start_date, end_date, employment_type,
                        weekly_hours, full_time_equivalent, work_schedule_id, status)
SELECT ('30000000-0000-0000-0000-0000000000' || right(e.id::text, 2))::uuid,
       e.id,
       'EMP-' || e.personnel_number || '-1',
       DATE '2020-01-01' + (right(e.id::text, 2)::int * 97) % 1500,
       NULL,
       CASE right(e.id::text, 2)
           WHEN '01' THEN 'ACADEMIC' WHEN '02' THEN 'ACADEMIC' WHEN '09' THEN 'ACADEMIC'
           WHEN '12' THEN 'ACADEMIC' WHEN '13' THEN 'ACADEMIC' WHEN '14' THEN 'TECHNICAL'
           WHEN '15' THEN 'ACADEMIC' WHEN '16' THEN 'ACADEMIC' WHEN '17' THEN 'STUDENT_ASSISTANT'
           WHEN '18' THEN 'TECHNICAL' WHEN '05' THEN 'TECHNICAL'
           ELSE 'ADMINISTRATIVE'
       END,
       CASE right(e.id::text, 2) WHEN '09' THEN 32.00 WHEN '11' THEN 20.00 ELSE 40.00 END,
       CASE right(e.id::text, 2) WHEN '09' THEN 0.800 WHEN '11' THEN 0.500 ELSE 1.000 END,
       ('40000000-0000-0000-0000-0000000000' || right(e.id::text, 2))::uuid,
       'ACTIVE'
FROM employee e
WHERE e.id::text LIKE '20000000-0000-0000-0000-0000000000__'
ON CONFLICT (id) DO NOTHING;

-- David Muster (13) has an ended earlier contract: people can hold several
-- employment relationships over time.
INSERT INTO employment (id, employee_id, external_employment_id, start_date, end_date, employment_type,
                        weekly_hours, full_time_equivalent, work_schedule_id, status) VALUES
    ('30000000-0000-0000-0000-000000000113', '20000000-0000-0000-0000-000000000013', 'EMP-P10013-0',
     DATE '2018-10-01', DATE '2019-12-31', 'STUDENT_ASSISTANT', 10.00, 0.250, NULL, 'ENDED')
ON CONFLICT (id) DO NOTHING;

UPDATE employee e
SET primary_employment_id = ('30000000-0000-0000-0000-0000000000' || right(e.id::text, 2))::uuid
WHERE e.id::text LIKE '20000000-0000-0000-0000-0000000000__'
  AND e.primary_employment_id IS NULL;

-- ------------------------------------------------------------------------ roles
INSERT INTO user_role (id, employee_id, role_id, valid_from)
SELECT md5(x.emp || x.role)::uuid, x.emp::uuid, r.id, DATE '2024-01-01'
FROM (VALUES
    ('20000000-0000-0000-0000-000000000001', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000002', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000002', 'SUPERVISOR'),
    ('20000000-0000-0000-0000-000000000003', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000003', 'FINANCIAL_APPROVER'),
    ('20000000-0000-0000-0000-000000000004', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000004', 'HR_ADMIN'),
    ('20000000-0000-0000-0000-000000000005', 'ERP_ADMIN'),
    ('20000000-0000-0000-0000-000000000006', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000006', 'TRAVEL_OFFICE'),
    ('20000000-0000-0000-0000-000000000007', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000007', 'TIME_ADMIN'),
    ('20000000-0000-0000-0000-000000000008', 'AUDITOR'),
    ('20000000-0000-0000-0000-000000000009', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000010', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000010', 'SUPERVISOR'),
    ('20000000-0000-0000-0000-000000000011', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000012', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000013', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000014', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000015', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000016', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000017', 'EMPLOYEE'),
    ('20000000-0000-0000-0000-000000000018', 'EMPLOYEE')
) AS x (emp, role)
JOIN role r ON r.name = x.role
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------- approval relations
-- 'supervisor' (02) approves academic staff of both faculties;
-- 'supervisor2' (10) approves central administration staff and 02.
-- 'finance' (03) is the financial approver for everyone's travel;
-- 'hradmin' (04) performs HR review.
INSERT INTO approval_relation (id, employee_id, approver_id, approval_type, priority, valid_from)
SELECT md5(x.emp || x.approver || t.type)::uuid, x.emp::uuid, x.approver::uuid, t.type, 1, DATE '2024-01-01'
FROM (VALUES
    ('20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000013', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000014', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000015', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000016', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000017', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000010'),
    ('20000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000010'),
    ('20000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000010'),
    ('20000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000010'),
    ('20000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000010'),
    ('20000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000010'),
    ('20000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000010'),
    ('20000000-0000-0000-0000-000000000018', '20000000-0000-0000-0000-000000000010')
) AS x (emp, approver)
CROSS JOIN (VALUES ('ABSENCE'), ('TRAVEL'), ('TIME_CORRECTION')) AS t (type)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_relation (id, employee_id, approver_id, approval_type, priority, valid_from)
SELECT md5(e.id::text || x.approver || x.type)::uuid, e.id, x.approver::uuid, x.type, 1, DATE '2024-01-01'
FROM employee e
CROSS JOIN (VALUES ('20000000-0000-0000-0000-000000000003', 'FINANCIAL'),
                   ('20000000-0000-0000-0000-000000000004', 'HR_REVIEW')) AS x (approver, type)
WHERE e.id::text LIKE '20000000-0000-0000-0000-0000000000__'
  AND e.id <> x.approver::uuid
ON CONFLICT (id) DO NOTHING;
