BEGIN TRANSACTION;
--;;
-- Create contentless FTS5 virtual table for link search
-- Using content="" makes it contentless (no data duplication)
-- content_rowid maps to the link.id for JOIN operations
CREATE VIRTUAL TABLE link_search USING fts5(
    title,
    url,
    content='',
    content_rowid='id',
    tokenize='trigram'
);
--;;
-- Trigger to maintain FTS5 index on INSERT
CREATE TRIGGER link_search_insert AFTER INSERT ON link BEGIN
    INSERT INTO link_search(rowid, title, url) 
    VALUES (new.id, new.title, new.url);
END;
--;;
-- Trigger to maintain FTS5 index on UPDATE
CREATE TRIGGER link_search_update AFTER UPDATE ON link BEGIN
    INSERT INTO link_search(link_search, rowid, title, url) VALUES('delete', old.id, old.title, old.url);
    INSERT INTO link_search(rowid, title, url) VALUES (new.id, new.title, new.url);
END;
--;;
-- Trigger to maintain FTS5 index on DELETE
CREATE TRIGGER link_search_delete AFTER DELETE ON link BEGIN
    INSERT INTO link_search(link_search, rowid, title, url) VALUES('delete', old.id, old.title, old.url);
END;
--;;
-- Populate FTS5 table with existing link data
INSERT INTO link_search(rowid, title, url)
SELECT id, title, url FROM link;
--;;
COMMIT;