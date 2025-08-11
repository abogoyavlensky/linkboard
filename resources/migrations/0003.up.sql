BEGIN TRANSACTION;
--;;
-- Insert a user
INSERT INTO "user" (session_id, account_number) VALUES ('07aa-ac8e-44c2-9b8f', 'JQ3L-MTAQ-G6XT-JV44');
--;;
-- Insert boards for the user
INSERT INTO "board" (title, user_id) VALUES ('Work', 1);
--;;
INSERT INTO "board" (title, user_id) VALUES ('Personal', 1);
--;;
-- Insert links for the Work board
INSERT INTO "link" (url, title, icon, board_id, user_id) VALUES
('https://example.com/work-docs', 'Work Docs', NULL, 1, 1),
('https://example.com/project-management', 'Project Management', NULL, 1, 1),
('https://example.com/work-calendar', 'Work Calendar', NULL, 1, 1),
('https://example.com/team-chat', 'Team Chat', NULL, 1, 1),
('https://example.com/analytics', 'Analytics Dashboard', NULL, 1, 1);
--;;
-- Insert links for the Personal board
INSERT INTO "link" (url, title, icon, board_id, user_id) VALUES
('https://example.com/personal-blog', 'Personal Blog', NULL, 2, 1),
('https://example.com/shopping', 'Shopping', NULL, 2, 1);
--;;
COMMIT;
