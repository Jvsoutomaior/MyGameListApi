-- Curated from the Steam store pages for apps 367520, 413150 and 504230.
-- Run after Flyway creates the games table.
INSERT INTO games (id, source, external_id, title, release_date, synchronized_at)
VALUES
    (gen_random_uuid(), 'STEAM', '367520', 'Hollow Knight', DATE '2017-02-24', now()),
    (gen_random_uuid(), 'STEAM', '413150', 'Stardew Valley', DATE '2016-02-26', now()),
    (gen_random_uuid(), 'STEAM', '504230', 'Celeste', DATE '2018-01-25', now())
ON CONFLICT (source, external_id) DO NOTHING;

SELECT external_id, title, release_date
FROM games
WHERE source = 'STEAM' AND external_id IN ('367520', '413150', '504230')
ORDER BY title;
