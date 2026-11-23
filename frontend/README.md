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

## Build an Android APK

Set `EXPO_PUBLIC_API_URL` in the Expo project's `preview` environment to your hosted HTTPS API. If the environment defines `EXPO_PUBLIC_API_URL_ANDROID`, set it to the same address. For your own installation, link the checkout to an Expo project you control.

From this directory, sign in and start the internal-distribution build:

```bash
npx eas-cli@latest login
npx eas-cli@latest build --platform android --profile preview
```

On the first build, link or create the Expo project and configure its Android signing credentials when prompted. Download the APK from the completed build and install it directly on Android. The app runs independently of Expo Go and the development server.

The `preview` profile uses the API URL from the Expo environment and enables the Finora sign-in screen. Enter your private access key on the Finora sign-in screen. The APK contains only the public API address and sign-in setting.
