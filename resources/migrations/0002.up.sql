BEGIN TRANSACTION;
--;;
-- Create the user table
CREATE TABLE "user" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR NOT NULL UNIQUE,
    account_number VARCHAR NULL UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Create index on session_id for faster session lookups
CREATE INDEX idx_user_session_id ON "user" (session_id);
--;;
-- Create index on account_number for faster account lookups
CREATE INDEX idx_user_account_number ON "user" (account_number);

--;;
-- Create the board table
CREATE TABLE "board" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE
);
--;;
-- Create the link table
CREATE TABLE "link" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    title TEXT,
    description TEXT,
    icon TEXT,
    board_id INTEGER NULL,
    user_id INTEGER NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (board_id) REFERENCES "board" (id) ON DELETE CASCADE
);
--;;
COMMIT;
