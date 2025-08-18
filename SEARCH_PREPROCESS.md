Steps to make it user-friendly

1. Preprocess the query string

Split input into tokens (by whitespace).

Quote tokens containing special chars (., @, /, -, :, ", *, etc.).

Optionally escape reserved keywords (OR, AND, NOT, NEAR) if they aren’t meant as operators.

Example:

input:  openai.com cool stuff
fts:    '"openai.com"' cool stuff


2. Add wildcards automatically

FTS5 only supports prefix wildcards (*).

Add * at the end of tokens so partial matches work:

"openai.com"* cool* stuff*


3. Normalize

Lowercase everything (SQLite FTS is case-insensitive, but normalizing avoids surprises).

Strip extra punctuation that shouldn’t matter (()[]{},;).

4. Wrap into a single MATCH expression

SELECT l.*, bm25(link_fts) AS rank
FROM link l
JOIN link_fts fts ON l.id = fts.rowid
WHERE link_fts MATCH :processed_query
ORDER BY rank;


5. Optional fallback

If the query ends up empty or invalid → fall back to a simple LIKE '%...%' search.

That way the user never sees an error.