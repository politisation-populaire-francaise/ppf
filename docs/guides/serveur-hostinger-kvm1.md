# Guide : louer et préparer le serveur Hostinger KVM 1

- Version : 7 novembre 2026 (deuxième version, mise en ligne de la préproduction).
- **Décision de la propriétaire (7 novembre 2026)** : « Je vais acheter le kvm2 ». Suivez ce guide avec le **KVM 2**.
- **Aucun achat sans votre accord écrit dans la conversation** sur le montant ci-dessous. Vous seule entrez les données de paiement.
- Durée : 20 minutes de votre côté, puis environ une heure du mien (sans action de votre part, sauf l'étape 5).

## 1. Le prix (relevé le 7 novembre 2026 sur hostinger.com/fr/vps, promotion « Black Friday »)

| Offre | Ce qu'elle contient | Payé aujourd'hui | Renouvellement |
|---|---|---|---|
| KVM 1, 24 mois | 1 processeur virtuel, 4 Go de mémoire, 50 Go de disque NVMe, 4 To de trafic | **131,76 € HT, soit environ 158,11 € TTC** (5,49 € HT par mois) | 11,99 € HT par mois (environ 14,39 € TTC), facturé 24 mois d'avance, à partir de novembre 2028 |
| **KVM 2, 24 mois** (recommandée depuis le 7 novembre 2026) | 2 processeurs virtuels, 8 Go de mémoire, 100 Go de disque NVMe, 8 To de trafic | **186,96 € HT, soit environ 224,35 € TTC** (7,79 € HT par mois) | 14,99 € HT par mois (environ 17,99 € TTC), facturé 24 mois d'avance, à partir de novembre 2028 |

- Achetez l'offre que vous avez validée dans la conversation. Le panier fait foi au moment de payer : si le total TTC dépasse **159 €** pour le KVM 1 ou **225 €** pour le KVM 2, arrêtez-vous et envoyez-moi une capture d'écran.
- **Mise à niveau** possible à tout moment depuis hPanel (vers KVM 2, 4 ou 8), en 10 minutes au plus, sans rien perdre (aide officielle de Hostinger). Avant l'achat, vous pouvez demander au chat de Hostinger si la durée déjà payée est déduite du prix de la mise à niveau.
- Garantie « satisfait ou remboursé » de 30 jours.
- Budget : avec les domaines (145,10 € TTC), le total atteint environ 303 € avec le KVM 1, environ 369 € avec le KVM 2, pour un budget de 300 € (plafond de 500 €). Voir l'entrée « Serveur de la préproduction : achat » du brainstorming du cahier des charges.

## 2. Ce qu'il faut avant de commencer

- Votre compte Hostinger (celui des domaines), avec la double authentification active.
- La **clé publique** du serveur, que j'ai créée sur votre ordinateur le 7 novembre 2026. Pour la copier :
  1. Ouvrez l'explorateur de fichiers, tapez `%USERPROFILE%\.ssh` dans la barre d'adresse et appuyez sur Entrée.
  2. Faites un clic droit sur **`ppf_server_ed25519.pub`** (celui qui finit par `.pub`, jamais l'autre), **« Ouvrir avec »**, **« Bloc-notes »**.
  3. La ligne commence par `ssh-ed25519 AAAA`. Sélectionnez tout (Ctrl + A) et copiez (Ctrl + C). Fermez le Bloc-notes sans rien modifier.
  - L'autre fichier, `ppf_server_ed25519` (sans `.pub`), est la clé secrète : il ne quitte jamais votre ordinateur et ne doit être envoyé à personne.

## 3. Commander

1. Connectez-vous sur https://hpanel.hostinger.com.
2. Allez sur https://www.hostinger.com/fr/vps (ou, dans hPanel, menu **VPS**, bouton **« Acheter un VPS »**). Si une fenêtre sur les cookies apparaît, cliquez sur **« Tout refuser »**.
3. Sous l'offre validée (**KVM 2** ou **KVM 1**), cliquez sur **« Sélectionner »**.
4. Dans le panier, vérifiez la durée : **24 mois**. Refusez toute option payante (sauvegardes quotidiennes, licences, panneau de contrôle, « Agent IA », adresse IP supplémentaire, nom de domaine offert : nous en avons déjà).
5. Vérifiez le total TTC (environ 224,35 € pour le KVM 2, 158,11 € pour le KVM 1), puis payez par carte. Gardez la facture : elle servira au remboursement par l'association.

## 4. Configurer le serveur dans hPanel

Juste après le paiement, Hostinger ouvre l'assistant de configuration du VPS.

1. **Lieu du serveur** : un centre de données de l'Union européenne **hors de France** : **Allemagne** (Francfort), **Lituanie** (Vilnius) ou **Pays-Bas** (Amsterdam), selon ce qui est proposé. Jamais la France, jamais le Royaume-Uni (décision du 5 novembre 2026).
2. **Type d'installation** : **« Système d'exploitation seul »** (« OS only », « Plain OS »), puis **Ubuntu 24.04** (64 bits). Pas de panneau de contrôle, pas d'application préinstallée (ni Docker, ni n8n, ni « Agent »).
3. **Clé SSH** : cliquez sur **« Ajouter une clé SSH »**, donnez-lui le nom `ordinateur-proprietaire` et collez la ligne copiée à l'étape 2 (Ctrl + V).
4. **Mot de passe root** : si l'assistant l'exige, cliquez sur le bouton qui génère un mot de passe aléatoire, ou tapez-en un long et unique, et enregistrez-le dans votre gestionnaire de mots de passe. Il ne servira plus : je désactive les connexions par mot de passe.
5. **Analyse antivirus** (Monarx) ou autre option facultative : décochez.
6. **Nom du serveur** (« hostname ») : `ppf-1`.
7. Terminez l'assistant. L'installation dure quelques minutes.
8. Sur la page du VPS, notez l'**adresse IPv4** (quatre nombres séparés par des points, par exemple `203.0.113.10`) et envoyez-la-moi dans la conversation.

Ne touchez à rien d'autre dans hPanel (pare-feu de hPanel, « Docker Manager », terminal IA) : la configuration est faite par mes scripts, et deux pare-feu qui se contredisent sont une source de pannes.

## 5. Ajouter les noms de la préproduction (DNS)

À faire seulement quand je vous le demande, avec l'adresse IPv4 de l'étape 4. C'est la seule modification du DNS avant le lancement public : le domaine principal lui-même ne change pas.

1. Dans hPanel, menu **« Domaines »**, cliquez sur **politisation-populaire-francaise.org**.
2. Dans le menu de gauche, cliquez sur **« DNS / Serveurs de noms »** (ou « Zone DNS »).
3. Dans la partie **« Gérer les enregistrements DNS »**, remplissez :
   - **Type** : `A`
   - **Nom** : `preprod`
   - **Pointe vers** : l'adresse IPv4 du serveur
   - **TTL** : laissez la valeur proposée (par exemple 14400)
4. Cliquez sur **« Ajouter un enregistrement »**.
5. Recommencez les étapes 3 et 4 avec, dans **Nom**, `equipe.preprod` (le back-office de l'équipe, lui aussi joignable seulement à travers le tunnel), le même type `A` et la même adresse IPv4, puis une troisième fois avec `push.preprod` (les notifications de la préproduction), et une quatrième fois avec `signature.equipe.preprod` (la page où l'équipe approuve une publication, servie par le service de signature lui-même).
6. N'ajoutez **pas** d'enregistrement `AAAA` pour ces quatre noms : la préproduction et le back-office ne doivent être joints qu'en IPv4, à travers le tunnel.

## 6. Ce que je fais ensuite, sans action de votre part

1. Préparation et durcissement (`infra/scripts/bootstrap-server.sh`) : mises à jour de sécurité automatiques, compte d'administration `ppf-admin` accessible par la seule clé, connexion root et mots de passe refusés, pare-feu (SSH, web et WireGuard seulement), journaux du système gardés en mémoire un jour au plus, Docker, tunnel WireGuard.
2. Création de vos deux profils WireGuard (ordinateur et téléphone) : guide `docs/guides/wireguard-preproduction.md`.
3. Premier déploiement de la préproduction, puis mise à jour automatique toutes les 10 minutes à partir de la branche principale.
4. Au moment du premier déploiement, je vous préviens : **vous choisissez le mot de passe des pages web de la préproduction** et vous le tapez vous-même, deux fois, dans l'onglet de terminal que j'ouvre (rien ne s'affiche pendant la frappe). Choisissez-le à ce moment-là : 12 caractères au moins, une phrase de plusieurs mots par exemple, et enregistrez-le dans votre gestionnaire de mots de passe. Il n'est écrit nulle part ailleurs : ni dans le dépôt, ni dans un message, ni dans un fichier ; le serveur n'en garde qu'une empreinte.

## 7. Après la mise en service

- Dans hPanel, page du VPS, activez les **alertes d'utilisation** (mémoire, disque) si elles sont proposées.
- Les sauvegardes hebdomadaires gratuites de Hostinger peuvent rester actives : elles ne remplacent pas les sauvegardes chiffrées chez un autre prestataire, prévues avant la production.
- Ne redémarrez pas, ne réinstallez pas et ne changez pas le système du serveur depuis hPanel sans m'en parler : une réinstallation efface tout.
