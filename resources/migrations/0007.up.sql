BEGIN TRANSACTION;
--;;
-- Add favorite column to link table
-- Default to FALSE for existing links
ALTER TABLE "link" ADD COLUMN favorite BOOLEAN DEFAULT FALSE;
--;;
-- Update all existing links to have favorite = FALSE (redundant but explicit)
UPDATE "link" SET favorite = FALSE WHERE favorite IS NULL;
--;;
COMMIT;