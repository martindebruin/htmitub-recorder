# HTMITUB Run Recorder

Android app for recording outdoor runs and syncing them to the HTMITUB running blog.

## Features

- GPS run recording with real-time distance and pace display
- Pause / resume support
- Background sync to the HTMITUB backend via bearer token auth
- Run history with sync status indicators

## Setup

1. Copy `local.properties.example` to `local.properties`
2. Fill in your values:
   - `sdk.dir` — path to your Android SDK
   - `server_url` — base URL of the HTMITUB backend
   - `bearer_token` — app bearer token (set in the backend `.env`)

## Building

```bash
./gradlew assembleDebug
```

## Installing on device

Enable USB debugging on your phone, connect via USB, then:

```bash
./gradlew installDebug
```

## Requirements

- Android 8.0+ (API 26)
- Location permission (required for GPS recording)
