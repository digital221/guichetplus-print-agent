# GUICHET+ Kiosk Android

Application kiosk Android autonome. Elle charge Guichet+ dans une WebView,
intercepte le flux ESC/POS `intent:base64` généré par le serveur et l'envoie
directement à une imprimante réseau sur le port 9100, sans RawBT.

Le menu Réglages permet de modifier l'URL du kiosque, l'IP et le port de
l'imprimante, puis d'imprimer un ticket de test.

Le profil **Universel · imprimantes chinoises** est activé par défaut. Il
convertit les commandes graphiques courantes vers `ESC *` afin de conserver le
logo sur les firmwares ESC/POS anciens ou partiels. Les profils natif et ancien
modèle restent disponibles dans les réglages.

La connexion Bluetooth classique et virtuelle utilise SPP/RFCOMM. Elle reste
ouverte entre deux tickets pour réduire fortement la latence et se reconnecte
automatiquement en cas de coupure. L'application essaie successivement les
modes RFCOMM sécurisé, non sécurisé et le canal série 1 pour les modules OEM.
Le sélecteur distingue clairement l'appairage Android de la sélection dans
GUICHET+ Kiosk : il liste les appareils déjà appairés, marque l'imprimante
active et permet d'ouvrir les réglages Bluetooth puis d'actualiser la liste.
Sur Android 13 et versions ultérieures, l'autorisation des appareils Wi-Fi à
proximité permet d'afficher le SSID réellement connecté dans la configuration.
Le formulaire se redimensionne avec le clavier et fait défiler automatiquement
le champ de mot de passe actif pour conserver la saisie et la confirmation visibles.
Le scanner QR du check-in utilise toujours la caméra frontale. Le WebView force
également le flux caméra en lecture automatique silencieuse et intégrée pour les
tablettes Samsung.

La première configuration exige un mot de passe administrateur d'au moins
8 caractères. Aucun mot de passe n'est fourni par défaut. L'application
n'enregistre qu'un condensat SHA-256 salé. Un condensat déjà présent sur
l'appareil continue d'être accepté, y compris s'il a été enregistré sans sel.
Si la borne est déjà configurée mais qu'aucun condensat n'est enregistré, sept
appuis dans le coin inférieur gauche demandent d'en choisir un avant d'ouvrir
la maintenance.

L'onboarding affiche des libellés permanents pour l'URL, l'imprimante et le
mot de passe. L'URL doit être complète, le mot de passe doit être confirmé et
peut être affiché temporairement. Aucune IP d'imprimante n'est préremplie sur
une nouvelle installation. Une IP réellement enregistrée reste conservée lors
des mises à jour.

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

L'orientation utilise le capteur complet de l'appareil (`fullSensor`) : portrait,
paysage et leurs positions inversées sont appliqués automatiquement sans
redémarrer la WebView.

Pendant l'exécution du kiosque, l'écran reste allumé et la luminosité ne diminue
pas. En mode Device Owner, le délai d'extinction est également porté à sa valeur
maximale et l'appareil reste éveillé lorsqu'il est alimenté.
