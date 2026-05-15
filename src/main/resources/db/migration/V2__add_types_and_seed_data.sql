-- ============================================================
-- V2 — Expand notification types and seed Figma demo data
--
-- 1. Drop and recreate the type CHECK constraint to include
--    the two new values: REMINDER, INSIGHT.
-- 2. Seed 7 rows for profile e1d0add5-b9c8-57b5-36e6-059991832f17
--    matching the frontend Figma mock so the UI can be tested.
--
-- Targets PostgreSQL 15+. `gen_random_uuid()` is core in PG 13+,
-- no extension required.
-- ============================================================

-- ---- 1. Refresh the type CHECK constraint --------------------
ALTER TABLE in_app_notifications
    DROP CONSTRAINT IF EXISTS chk_in_app_notifications_type;

ALTER TABLE in_app_notifications
    ADD CONSTRAINT chk_in_app_notifications_type
    CHECK (type IN ('BOOKING', 'STREAK', 'CHAT', 'REMINDER', 'INSIGHT'));

-- ---- 2. Seed Figma demo data ---------------------------------
-- Stagger `created_at` so the inbox sorts newest-first deterministically.

INSERT INTO in_app_notifications
    (notification_id, profile_id, title, message, type, is_read, created_at)
VALUES
    (gen_random_uuid(),
     'e1d0add5-b9c8-57b5-36e6-059991832f17',
     'Tin nhắn mới từ Dave',
     '52 Tin nhắn chưa đọc',
     'CHAT',
     FALSE,
     NOW() - INTERVAL '5 minutes'),

    (gen_random_uuid(),
     'e1d0add5-b9c8-57b5-36e6-059991832f17',
     'Bạn chưa cập nhật cảm xúc',
     '5 ngày chưa cập nhật',
     'REMINDER',
     FALSE,
     NOW() - INTERVAL '2 hours'),

    (gen_random_uuid(),
     'e1d0add5-b9c8-57b5-36e6-059991832f17',
     'Nhật kí tháng này',
     '21/30 Nhật kí đã viết cho tháng này',
     'STREAK',
     FALSE,
     NOW() - INTERVAL '6 hours'),

    (gen_random_uuid(),
     'e1d0add5-b9c8-57b5-36e6-059991832f17',
     'Bài tập hàng tuần hoàn thành',
     '22 phút thiền mỗi ngày',
     'STREAK',
     TRUE,
     NOW() - INTERVAL '1 day'),

    (gen_random_uuid(),
     'e1d0add5-b9c8-57b5-36e6-059991832f17',
     'Stress cải thiện',
     'Mức độ stress hiện tại là 3',
     'INSIGHT',
     TRUE,
     NOW() - INTERVAL '2 days'),

    (gen_random_uuid(),
     'e1d0add5-b9c8-57b5-36e6-059991832f17',
     '05:25AM',
     'Therapy with Dr. Freud AI',
     'BOOKING',
     FALSE,
     NOW() - INTERVAL '3 days'),

    (gen_random_uuid(),
     'e1d0add5-b9c8-57b5-36e6-059991832f17',
     '7h 50m',
     'Sức khỏe giấc ngủ cải thiện',
     'INSIGHT',
     FALSE,
     NOW() - INTERVAL '5 days');
