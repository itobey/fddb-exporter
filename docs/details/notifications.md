# Notifications

FDDB Exporter can send a Telegram message when the **scheduled** export cannot parse a day. It is the one thing that
happens without you watching, so it is the one thing worth a push notification.

Notifications are enabled by default, but nothing is sent until a bot token and a chat id are configured — with the
defaults in place the feature is on and idle.

## What triggers a message

| Situation | Notification | Log |
|---|---|---|
| The scheduled export cannot parse yesterday's diary page | **Message sent** | `warn` |
| Logging in to fddb.info fails | none | `error: not logged in - skipping job execution` |
| Another export was already running, so the scheduled run was skipped | none | `warn` |
| A manual export via the REST API, Web UI or MCP fails | none | depends on the caller |

Only the scheduled export notifies, and only for a parse failure. The reasoning: a scheduled run is unattended, and a
day that cannot be parsed is silently missing from your data until you notice. Everything else either has someone
watching the response, or is not a failure — a skipped run means an export was already happening, and yesterday is
picked up by the next run.

A parse failure most often means **there was nothing logged for that day** — see
[Troubleshooting](/details/troubleshooting.md#a-day-comes-back-as-unsuccessful). If you routinely skip logging days,
expect a message on those mornings, or turn notifications off.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FDDB-EXPORTER_NOTIFICATION_ENABLED` | true | Send a Telegram message when a scheduled export fails to parse |
| `FDDB-EXPORTER_NOTIFICATION_TELEGRAM_TOKEN` | - | Bot token from BotFather |
| `FDDB-EXPORTER_NOTIFICATION_TELEGRAM_CHATID` | - | The chat to send to |

::: tip
`export` in a POSIX shell rejects the hyphens — use the all-underscore form
(`FDDB_EXPORTER_NOTIFICATION_ENABLED`) there. See the
[note on the hyphens](/details/configuration.md#a-note-on-the-hyphens).
:::

The token is a credential: prefer a Kubernetes secret, a Docker secret or a
[Jasypt-encrypted property](/details/configuration.md#encrypting-credentials) over a plain environment variable.

## Getting a bot token and a chat id

1. In Telegram, open a chat with [@BotFather](https://t.me/botfather) and send `/newbot`. Follow the prompts for a name
   and a username. BotFather replies with a token of the form `123456789:AAF...` — that is
   `FDDB-EXPORTER_NOTIFICATION_TELEGRAM_TOKEN`.
2. Send any message to your new bot. A bot cannot start a conversation, so without this first message it has no chat to
   reply to.
3. Fetch the chat id:

   ```bash
   curl "https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates"
   ```

   In the response, `result[0].message.chat.id` is your chat id — a number, negative for groups. That is
   `FDDB-EXPORTER_NOTIFICATION_TELEGRAM_CHATID`.

## Turning it off

```bash
docker run -e 'FDDB-EXPORTER_NOTIFICATION_ENABLED=false' ghcr.io/itobey/fddb-exporter
```

Parse failures are still logged at `warn`, so nothing becomes invisible — it just stops reaching your phone.

## It is enabled but nothing arrives

- **`Failed to send Telegram message` in the log.** The message was attempted and Telegram rejected it. The response
  description is logged with it: a wrong token gives `Unauthorized`, a wrong or never-messaged chat gives
  `chat not found`.
- **No log line at all.** Nothing triggered a notification. Only a parse failure in the *scheduled* run does; check
  whether the scheduler ran at all (`/actuator/scheduledtasks` lists the registered cron tasks) and whether
  `FDDB-EXPORTER_SCHEDULER_ENABLED` is still `true`.
- **Messages render oddly.** Messages are sent with Telegram's Markdown parse mode.
