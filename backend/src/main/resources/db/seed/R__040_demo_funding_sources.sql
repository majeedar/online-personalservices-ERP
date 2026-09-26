-- DEMO DATA ONLY (ADR-009). Funding sources: unit cost centres and a few fictitious projects.
INSERT INTO funding_source (id, cost_centre, project_code, fund_code, description)
SELECT md5('funding-' || x.cc || coalesce(x.project, '') || x.fund)::uuid, x.cc, x.project, x.fund, x.description
FROM (VALUES
    ('CC-2200', NULL,           'BUDGET',  'Institute of Computer Science – basic budget'),
    ('CC-2201', 'PRJ-ORD-2026', 'EU-HE',   'Open Research Data (EU Horizon, fictitious)'),
    ('CC-2100', NULL,           'BUDGET',  'Institute of Applied Physics – basic budget'),
    ('CC-2100', 'PRJ-QM-17',    'DFG',     'Quantum Materials (research council, fictitious)'),
    ('CC-3100', NULL,           'BUDGET',  'Department of History – basic budget'),
    ('CC-3200', NULL,           'BUDGET',  'Department of Linguistics – basic budget'),
    ('CC-1100', NULL,           'BUDGET',  'Central Administration – basic budget'),
    ('CC-1130', NULL,           'BUDGET',  'IT Services – basic budget')
) AS x (cc, project, fund, description)
ON CONFLICT DO NOTHING;
