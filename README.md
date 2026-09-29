# StatBoard

Player stats and leaderboards for PowerNukkitX, with floating text leaderboards you can place anywhere in your world.

## What it does

- **Tracks every player:** kills, deaths, K/D ratio, mobs killed, blocks broken, blocks placed, playtime, best kill streak and balance.
- **`/stats`** shows your stats, or another player's, in a clean menu with a button to the leaderboards.
- **`/top`** opens the leaderboards menu. Each leaderboard shows the top 10 and your own rank.
- **Floating leaderboards:** stand where you want one, type `/stats hologram kills`, done. They refresh on their own and show up again after a reconnect or a world change.
- **Balance leaderboard** works with LlamaEconomy, EconomyAPI or FrenchEconomy, detected automatically.
- **Fair K/D:** players need a minimum number of kills to appear in the K/D leaderboard.
- Every category can be turned off and every text can be edited.

## Install

1. Drop `StatBoard.jar` in your `plugins` folder.
2. Restart the server.

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/stats [player]` | Show stats | `statboard.use` (everyone) |
| `/top` | Open the leaderboards menu | `statboard.use` (everyone) |
| `/top <category>` | Open one leaderboard | `statboard.use` (everyone) |
| `/stats hologram <category>` | Place a floating leaderboard where you stand | `statboard.admin` (op) |
| `/stats hologram remove` | Remove the closest floating leaderboard (5 blocks) | `statboard.admin` (op) |
| `/stats reload` | Reload the config | `statboard.admin` (op) |

Categories: `kills`, `deaths`, `kdr`, `mobs`, `broken`, `placed`, `playtime`, `streak`, `balance`.

## Config

```yaml
leaderboard-size: 10
hologram-size: 10
hologram-refresh-seconds: 30
save-interval-seconds: 60
kdr-min-kills: 10
categories:
  kills: true
  deaths: true
  kdr: true
  mobs: true
  broken: true
  placed: true
  playtime: true
  streak: true
  balance: true
```

Every message is in the `messages` section of `config.yml`. Stats are saved in `stats.yml`, floating leaderboards in `holograms.yml`.

## Good to know

- The balance of a player is read while they are online, then kept for the leaderboard.
- Playtime is counted every minute a player is online.

## Compatibility

PowerNukkitX 3.x (API 3.0.0), tested on 3.0.5 with Minecraft Bedrock 1.26.50.

## About

Made by DeepSlate Dev. Released under the MIT license, free to use on any server.
