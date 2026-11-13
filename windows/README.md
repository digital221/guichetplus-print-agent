# GUICHET+ Kiosk Windows

Agent Electron local écoutant uniquement sur `127.0.0.1:3210`. Il accepte les
flux ESC/POS signés par l'application Guichet+ depuis l'origine de production,
les envoie à l'imprimante et démarre automatiquement avec Windows.

Trois transports sont disponibles et mémorisés :

- réseau TCP, généralement sur le port `9100` ;
- file d'impression Windows RAW pour une imprimante USB ou Bluetooth installée ;
- port série Bluetooth virtuel `COM`, de 9 600 à 921 600 bauds selon le matériel.

Le bouton d'actualisation détecte les imprimantes et ports série disponibles.
Une imprimante USB doit disposer de son pilote Windows. Une imprimante Bluetooth
doit être appairée dans Windows avant sa sélection dans GUICHET+ Kiosk.

Le profil **Universel · imprimantes chinoises** convertit les formats raster
modernes vers `ESC *` avant l'envoi. Le logo reste ainsi imprimable sur les
modèles ESC/POS génériques qui ne comprennent pas toutes les commandes Epson.

L'installateur NSIS permet de choisir le dossier, crée les raccourcis et lance
l'écran de configuration à la fin de l'installation.
