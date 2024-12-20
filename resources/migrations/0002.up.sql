-- Create the user table
CREATE TABLE "user" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sync_code VARCHAR NOT NULL UNIQUE
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Create the board table
CREATE TABLE "board" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
);

-- Create the link table
CREATE TABLE "link" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    icon TEXT,
    board_id INTEGER NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (board_id) REFERENCES board (id) ON DELETE CASCADE
);
