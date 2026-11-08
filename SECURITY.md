# Politique de sécurité

La sécurité et la vie privée des utilisateurs passent avant tout. Nous remercions toute personne qui nous aide à les protéger.

Vous pensez avoir trouvé une faille dans le site, les applications ou le serveur de Politisation Populaire Française ? Merci de nous la signaler **en privé**, sans ouvrir de ticket public.

La politique complète (engagements, règles d'une recherche de bonne foi, remerciements) est publiée sur le site : `/securite`, et les moyens de contact à jour figurent dans `/.well-known/security.txt` (RFC 9116). Les deux sont produits par le serveur (`web/.../SecurityPages.kt`), à partir des réglages de chaque environnement.

## Comment nous écrire

1. **Message privé chiffré** (Nostr, NIP-17) à la clé de l'équipe sécurité indiquée dans `security.txt` (champ `Contact: nostr:npub1…`), depuis l'application PPF ou toute application Nostr.
2. **Signalement privé du dépôt** (« Report a vulnerability », onglet *Security*), dès que le dépôt est publié sur GitHub.
3. Vous pouvez aussi signaler la faille à l'ANSSI, qui protège l'identité de la personne qui signale de bonne foi (article L. 2321-4 du code de la défense) : https://cyber.gouv.fr/signaler-une-vulnerabilite-un-incident

Dites-nous ce que vous avez trouvé et où (version, page, écran), comment le reproduire, et ce qu'un attaquant pourrait en faire. N'y joignez jamais les données personnelles d'autres personnes.

## Nos engagements (projet, à faire relire par un avocat)

- Accusé de réception sous 72 heures, premier avis sous dix jours, puis des nouvelles régulières de la correction.
- Correction d'une faille grave au plus vite, au plus tard sous 90 jours.
- Publication conjointe après la correction, et remerciements publics sous le nom ou le pseudonyme de votre choix.
- Aucune poursuite contre une recherche menée de bonne foi : n'accédez qu'à vos propres comptes, arrêtez-vous dès que la faille est démontrée, ni déni de service, ni piège tendu à l'équipe ou aux utilisateurs.

## In English

Please report vulnerabilities privately: an encrypted Nostr private message (NIP-17) to the key listed in `/.well-known/security.txt`, or the private vulnerability reporting of this repository once it is on GitHub. We answer in French or English, acknowledge within 72 hours and never take legal action against good-faith research that follows the rules of the `/securite` page.

## Versions couvertes

Le projet est en phase 0 (fondations). Seule la branche `main` est maintenue.
