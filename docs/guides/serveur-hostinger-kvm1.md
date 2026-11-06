# Guide : louer et préparer le serveur Hostinger KVM 1

- Version : 5 novembre 2026, première version.
- **À n'utiliser qu'au moment du premier déploiement**, quand il y aura un squelette à mettre en préproduction. Pas avant : un serveur inutilisé coûte de l'argent.
- Les prix sont à vérifier le jour de la commande ; je vous annoncerai le total avant tout achat. Repère du 5 novembre 2026 : KVM 1 à 5,49 € par mois hors taxes en payant 24 mois d'avance, soit environ 132 € HT (158 € TTC), puis 11,99 € par mois au renouvellement.

## 1. Ce qu'il faut

- Votre compte Hostinger (celui des domaines), avec la double authentification active.
- Une clé SSH que je créerai sur votre ordinateur avant la commande ; le serveur n'acceptera que cette clé, jamais un mot de passe.
- 20 minutes.

## 2. Commander

1. Connectez-vous sur https://hpanel.hostinger.com, puis menu **VPS**, bouton **« Acheter un VPS »** (ou page https://www.hostinger.com/fr/hebergement-vps).
2. Choisissez **KVM 1** (1 vCPU, 4 Go de mémoire, 50 Go NVMe). Refusez toute offre plus grande ou toute option payante (sauvegardes payantes, licences, « Agent IA »).
3. Durée : **24 mois** (c'est ce qui donne le prix de 5,49 €). Vérifiez le total TTC et comparez-le au montant que je vous aurai annoncé.
4. Payez par carte.

## 3. Configurer le serveur dans hPanel

1. **Lieu** : choisissez un centre de données dans l'Union européenne **hors de France** : Lituanie (Vilnius), Allemagne (Francfort) ou Pays-Bas (Amsterdam). Jamais la France, jamais le Royaume-Uni (décision du 5 novembre 2026).
2. **Système d'exploitation** : **Ubuntu 24.04 LTS**, version « nue » (sans panneau, sans application préinstallée).
3. **Clé SSH** : collez la clé publique que je vous aurai donnée (fichier `.pub`). Ne définissez pas de mot de passe root si l'écran le permet ; s'il l'impose, choisissez-en un long et unique, nous désactiverons ensuite la connexion par mot de passe.
4. **Nom du serveur** : `ppf-1`.
5. Notez l'**adresse IP** affichée et envoyez-la-moi. N'installez rien d'autre depuis hPanel.

## 4. Ce que je ferai ensuite (sans action de votre part)

1. Durcissement : pare-feu (seuls les ports 22, 80, 443 et WireGuard ouverts), SSH par clé uniquement, pas de connexion root, mises à jour de sécurité automatiques, utilisateurs système séparés pour la production et la préproduction.
2. Installation de Docker et déploiement de `infra/` : proxy, préproduction (production au lancement public).
3. Tunnel WireGuard : je vous remettrai un profil pour votre ordinateur et un pour votre téléphone, avec le guide d'installation.
4. DNS : avec votre accord, je vous guiderai pour pointer les domaines vers le serveur, extension par extension, le jour du lancement public seulement.
5. Sauvegardes chiffrées hors site : prestataire et coût à valider avec vous avant.

## 5. Après la mise en service

- Dans hPanel, activez les **alertes d'utilisation** (mémoire, disque) pour être prévenue par e-mail.
- Ne redémarrez pas et ne réinstallez pas le serveur depuis hPanel sans m'en parler : ces actions sont irréversibles.
