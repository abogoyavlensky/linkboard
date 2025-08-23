BEGIN TRANSACTION;
--;;
-- Add favorite column to board table
-- Default to FALSE for existing boards
ALTER TABLE "board" ADD COLUMN favorite BOOLEAN DEFAULT FALSE;
--;;
-- Update all existing boards to have favorite = FALSE (redundant but explicit)
UPDATE "board" SET favorite = FALSE WHERE favorite IS NULL;
--;;
COMMIT;