BEGIN TRANSACTION;
--;;
-- Create index on session_id for faster session lookups
CREATE INDEX idx_board_user_id_title ON "board" (user_id, title);
--;;
COMMIT;
