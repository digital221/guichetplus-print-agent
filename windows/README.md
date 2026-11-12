# Guichet+ Print Agent Windows

Agent Electron local écoutant uniquement sur `127.0.0.1:3210`. Il accepte les
flux ESC/POS signés par l'application Guichet+ depuis l'origine de production,
les envoie à l'imprimante réseau et démarre automatiquement avec Windows.

Le profil **Universel · imprimantes chinoises** convertit les formats raster
modernes vers `ESC *` avant l'envoi. Le logo reste ainsi imprimable sur les
modèles ESC/POS génériques qui ne comprennent pas toutes les commandes Epson.

L'installateur NSIS permet de choisir le dossier, crée les raccourcis et lance
l'écran de configuration à la fin de l'installation.
