# Guichet+ Print Agent Android

Application kiosk Android autonome. Elle charge Guichet+ dans une WebView,
intercepte le flux ESC/POS `intent:base64` généré par le serveur et l'envoie
directement à une imprimante réseau sur le port 9100, sans RawBT.

Le menu Réglages permet de modifier l'URL du kiosque, l'IP et le port de
l'imprimante, puis d'imprimer un ticket de test.

Après une réinitialisation d'usine, le mode Device Owner s'active avec :
`adb shell dpm set-device-owner sn.digitalfactory.guichetplus.printagent/.KioskDeviceAdminReceiver`.
L'application devient le launcher HOME et utilise Lock Task. Sept appuis rapides
dans le coin inférieur gauche ouvrent les réglages Guichet+, Wi-Fi ou Android.

La WebView autorise la caméra à la demande pour le lecteur QR du parcours
Guichet+, accepte les scanners USB/Bluetooth HID comme saisie clavier, recharge
la page après une reconnexion réseau et effectue un rafraîchissement de garde
toutes les 15 minutes uniquement après au moins 5 minutes sans interaction.
