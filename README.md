# GUICHET+ Kiosk

Clients officiels Digital Factory pour exploiter Guichet+ en mode borne et
imprimer directement les tickets sans dialogue système ni RawBT.

- **Android** : launcher kiosk WebView, configuration URL/Wi-Fi/imprimante,
  lecteur QR/caméra, impression ESC/POS réseau et maintenance protégée.
- **Windows** : agent d’impression local lancé avec Windows, compatible réseau,
  imprimantes USB/Bluetooth Windows et ports série Bluetooth `COM`.

Les deux clients consomment le flux ESC/POS composé par Guichet+ afin de
conserver le logo, le numéro agrandi, le code-barres et la découpe.

Le profil d'impression **Universel** convertit automatiquement les images
`GS v 0` et `GS ( L` vers le format historique `ESC *`. Le logo reste ainsi
imprimable sur la majorité des imprimantes thermiques ESC/POS génériques,
notamment les modèles OEM chinois dont le firmware est partiellement compatible.

## Téléchargements

Les installateurs officiels sont disponibles dans la
[dernière version publiée](https://github.com/digital221/guichetplus-print-agent/releases/latest).

## Compilation Android

```bash
cd android
gradle :app:assembleDebug
```

La publication Google Play utilise une clé d'upload externe au dépôt. Sans
ces valeurs, `gradle :app:bundleRelease` produit un bundle non signé. Les
propriétés Gradle (`-P` ou `~/.gradle/gradle.properties`) sont lues en
priorité, puis les variables d'environnement du même nom. L'alias par défaut
est `guichetplus-upload`. Le chemin du keystore est absolu, ou relatif au
module `android/app`.

```bash
cd android
gradle :app:bundleRelease \
  -PGUICHETPLUS_KEYSTORE_PATH=/chemin/absolu/upload.jks \
  -PGUICHETPLUS_KEYSTORE_PASSWORD='mot-de-passe-du-keystore' \
  -PGUICHETPLUS_KEY_ALIAS=guichetplus-upload \
  -PGUICHETPLUS_KEY_PASSWORD='mot-de-passe-de-la-cle'
```

## Compilation Windows

```bash
cd windows
npm ci
npm run dist
```

Copyright © Digital Factory. Tous droits réservés.
