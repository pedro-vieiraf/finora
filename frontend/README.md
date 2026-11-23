# Finora frontend

Finora's React Native and TypeScript client uses Expo Router for navigation. It includes a mobile dashboard and a desktop web layout for accounts, transactions, recurring commitments, and card bills.

See the [project README](../README.md) for the API setup, Open Finance configuration, and test commands.

## Start developing

From this directory, configure `EXPO_PUBLIC_API_URL` in a local `.env` file so it points to a running Finora API. Then install dependencies and start Expo:

```bash
npm ci
npx expo start
```

Routes live in `src/app`, shared UI in `src/components`, and REST clients in `src/services`. Use the computer's LAN address for a physical device. On Android, `EXPO_PUBLIC_API_URL_ANDROID` can override the shared URL.
