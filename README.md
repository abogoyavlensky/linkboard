# Linkboard

Self-hosted personal bookmark manager.

## Features
- [x] Clojure + SQLite + HTMX + AlpineJS + TailwindCSS
- [x] Quick way to start your next Clojure project
- [x] PWA ready + icons https://evilmartians.com/chronicles/how-to-favicon-in-2021-six-files-that-fit-most-needs
- [ ] Security middlewares: https://github.com/ring-clojure/ring-defaults/blob/master/src/ring/middleware/defaults.clj
  - https://github.com/kit-clj/kit/blob/7043800d87bf7f845b08295e11be3b43a16298e8/libs/deps-template/resources/io/github/kit_clj/kit/resources/system.edn


## Local development

Install [mise-en-place](https://mise.jdx.dev/getting-started.html#quickstart) (or [asdf](https://asdf-vm.com/guide/getting-started.html)),
then to install system deps run:

```shell
mise install
```

Check all available commands:

```shell
bb tasks 
```

### Notes

Optionally you could initiate `lefthook` tool to perform git-hook before every commit:

```
lefthook install
```

## Deploy from local machine

Create `.env` file with variables: 
```bash
SERVER_IP=192.168.0.1
REGISTRY_USERNAME=your-github-username
REGISTRY_PASSWORD=personal-github-token
APP_DOMAIN=app.domain.com
SESSION_SECRET_KEY=secret-key
SENTRY_DSN=sentry-dsn
```

Install ruby and kamal:

```shell
mise install ruby
gem install kamal -v 2.3.0
```

First deploy to the fresh server:

```shell
bb kamal setup
```

### Regular deployment

```shell
bb kamal deploy
```

## Deploy from Github Actions

Setup secrets fro Actions:

```shell
SERVER_IP=192.168.0.1
APP_DOMAIN=app.domain.com
SSH_PRIVATE_KEY=secret-ssh-key
```

### Notes

- `SSH_PRIVATE_KEY` - a new SSH private key **without password** that you created and added public part of it to the server's `~/.ssh/authorized_keys` to authorize from CI-worker.

To generate SSH keys, run:

```shell
ssh-keygen -t ed25519 -C "your_email@example.com"
```

#### Tests

To be able to run tests locally you have to install Chrome  browser. 

## TODO

- [x] Setup SQLite: `PRAGMA journal_mode=WAL` https://til.simonwillison.net/sqlite/enabling-wal-mode 

Base color for primary color: #c792e9
