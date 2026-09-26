-- DEMO DATA ONLY (ADR-009). A small generic public-holiday calendar.
-- Real deployments import the regional calendar instead.

INSERT INTO holiday (id, date, name, region_code)
SELECT md5('holiday-' || h.date::text || '-DEFAULT')::uuid, h.date, h.name, 'DEFAULT'
FROM (VALUES
    (DATE '2026-01-01', 'New Year''s Day'),
    (DATE '2026-04-03', 'Good Friday'),
    (DATE '2026-04-06', 'Easter Monday'),
    (DATE '2026-05-01', 'Labour Day'),
    (DATE '2026-05-14', 'Ascension Day'),
    (DATE '2026-05-25', 'Whit Monday'),
    (DATE '2026-10-03', 'Day of Unity'),
    (DATE '2026-12-25', 'Christmas Day'),
    (DATE '2026-12-26', 'Second Day of Christmas'),
    (DATE '2027-01-01', 'New Year''s Day'),
    (DATE '2027-03-26', 'Good Friday'),
    (DATE '2027-03-29', 'Easter Monday'),
    (DATE '2027-05-01', 'Labour Day'),
    (DATE '2027-05-06', 'Ascension Day'),
    (DATE '2027-05-17', 'Whit Monday'),
    (DATE '2027-10-03', 'Day of Unity'),
    (DATE '2027-12-25', 'Christmas Day'),
    (DATE '2027-12-26', 'Second Day of Christmas')
) AS h (date, name)
ON CONFLICT (date, region_code) DO NOTHING;
