# Guichet+ Print Agent

Clients officiels Digital Factory pour exploiter Guichet+ en mode borne et
imprimer directement les tickets sans dialogue système ni RawBT.

- **Android** : launcher kiosk WebView, configuration URL/Wi-Fi/imprimante,
  lecteur QR/caméra, impression ESC/POS réseau et maintenance protégée.
- **Windows** : agent d’impression local lancé avec Windows et écran de
  configuration.

Les deux clients consomment le flux ESC/POS composé par Guichet+ afin de
conserver le logo, le numéro agrandi, le code-barres et la découpe.

## Téléchargements

Les installateurs officiels sont disponibles dans la
[dernière version publiée](https://github.com/digital221/guichetplus-print-agent/releases/latest).

## Compilation Android

```bash
cd android
gradle :app:assembleDebug
```

La publication Google Play utilise une clé d'upload externe au dépôt. Les
variables `GUICHETPLUS_KEYSTORE_PATH`, `GUICHETPLUS_KEYSTORE_PASSWORD`,
`GUICHETPLUS_KEY_ALIAS` et `GUICHETPLUS_KEY_PASSWORD` permettent de produire
un bundle signé avec `gradle :app:bundleRelease`.

## Compilation Windows

```bash
cd windows
npm ci
npm run dist
```

Copyright © Digital Factory. Tous droits réservés.
