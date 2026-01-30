BEGIN TRANSACTION;
--;;
-- Drop existing triggers
DROP TRIGGER IF EXISTS link_search_insert;
--;;
DROP TRIGGER IF EXISTS link_search_update;
--;;
DROP TRIGGER IF EXISTS link_search_delete;
--;;
-- Drop existing FTS5 table
DROP TABLE IF EXISTS link_search;
--;;
-- Recreate FTS5 virtual table with description column
CREATE VIRTUAL TABLE link_search USING fts5(
    title,
    url,
    description,
    content='',
    content_rowid='id',
    tokenize='trigram'
);
--;;
-- Recreate trigger to maintain FTS5 index on INSERT
CREATE TRIGGER link_search_insert AFTER INSERT ON link BEGIN
    INSERT INTO link_search(rowid, title, url, description)
    VALUES (new.id, new.title, new.url, new.description);
END;
--;;
-- Recreate trigger to maintain FTS5 index on UPDATE
CREATE TRIGGER link_search_update AFTER UPDATE ON link BEGIN
    INSERT INTO link_search(link_search, rowid, title, url, description) VALUES('delete', old.id, old.title, old.url, old.description);
    INSERT INTO link_search(rowid, title, url, description) VALUES (new.id, new.title, new.url, new.description);
END;
--;;
-- Recreate trigger to maintain FTS5 index on DELETE
CREATE TRIGGER link_search_delete AFTER DELETE ON link BEGIN
    INSERT INTO link_search(link_search, rowid, title, url, description) VALUES('delete', old.id, old.title, old.url, old.description);
END;
--;;
-- Populate FTS5 table with existing link data including description
INSERT INTO link_search(rowid, title, url, description)
SELECT id, title, url, description FROM link;
--;;
COMMIT;
