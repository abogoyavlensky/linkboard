BEGIN TRANSACTION;
--;;
-- Insert a use, account number = 'JQ3L-MTAQ-G6XT-JV44'
INSERT INTO "user" (session_id, account_number, password) VALUES ('07aa-ac8e-44c2-9b8f', 'JQ3L-MTAQ', 'bcrypt+sha512$43e93a7f649c7f031a8c9620e89f726d$12$ed98bdae8bd8a7570cb63a932d2e1ea1a5aae7397da7e656');
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
