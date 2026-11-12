# Guichet+ Print Agent Android

Application kiosk Android autonome. Elle charge Guichet+ dans une WebView,
intercepte le flux ESC/POS `intent:base64` généré par le serveur et l'envoie
directement à une imprimante réseau sur le port 9100, sans RawBT.

Le menu Réglages permet de modifier l'URL du kiosque, l'IP et le port de
l'imprimante, puis d'imprimer un ticket de test.

Le profil **Universel · imprimantes chinoises** est activé par défaut. Il
convertit les commandes graphiques courantes vers `ESC *` afin de conserver le
logo sur les firmwares ESC/POS anciens ou partiels. Les profils natif et ancien
modèle restent disponibles dans les réglages.

La première configuration exige un mot de passe administrateur d'au moins
8 caractères. Aucun mot de passe n'est fourni par défaut. L'application
n'enregistre qu'un condensat SHA-256 salé. Un condensat déjà présent sur
l'appareil continue d'être accepté, y compris s'il a été enregistré sans sel.
Si la borne est déjà configurée mais qu'aucun condensat n'est enregistré, sept
appuis dans le coin inférieur gauche demandent d'en choisir un avant d'ouvrir
la maintenance.

L'identifiant installé sur l'appareil est `sn.digitalfactory.guichetplus.kiosk`.
Le paquet Java des composants reste `sn.digitalfactory.guichetplus.printagent`.
Après une réinitialisation d'usine, le mode Device Owner s'active avec :
`adb shell dpm set-device-owner sn.digitalfactory.guichetplus.kiosk/sn.digitalfactory.guichetplus.printagent.KioskDeviceAdminReceiver`.
L'application devient le launcher HOME et utilise Lock Task. Sept appuis rapides
dans le coin inférieur gauche ouvrent les réglages Guichet+, Wi-Fi ou Android.

La WebView autorise la caméra à la demande pour le lecteur QR du parcours
Guichet+, accepte les scanners USB/Bluetooth HID comme saisie clavier, recharge
la page après une reconnexion réseau et effectue un rafraîchissement de garde
toutes les 15 minutes uniquement après au moins 5 minutes sans interaction.
