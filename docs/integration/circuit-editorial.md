# Circuit éditorial (files `editorial.comprendre` et `editorial.voir`) : conception, version 0.1

Rédigé le 7 novembre 2026 par le pôle « Comprendre », sur le plan approuvé par le chef d'équipe le même jour. **Document de conception ; les étapes 1 à 4 sont codées (section « État »).** Le schéma déclaratif (section 1) est soumis à l'accord du pôle « Voir » avant toute ligne de code ; l'interface du service de signature (section 6) et les points d'extension du socle (section 5.4) sont à confirmer par le Directeur technique.

- Sources : cahier fonctionnel 2.0, modules M13 (Comprendre), M2 à M10 (Voir), M15 à M17 (back-office, double validation) ; cahier des charges, sections 5.6 (IA : « tout contenu produit avec l'aide d'une IA affiche son origine »), 9.1 et 9.3 (méthode et charte) ; contrat du back-office `docs/integration/back-office.md`, version 1.1 (sections 5 et 7) ; `CLAUDE.md`, section 7 (signature des contenus, port de publication unique, clé de la plateforme jamais en clair sur le serveur principal).
- Numéros : décisions dans le bloc de « Comprendre » (D-090 à D-099), la première libre au moment du code ; migration Flyway provisoire `V10__editorial.sql`, renumérotée à l'entrée dans `main` (registre de l'équipe).
- Ce qui ne change pas : le format des événements (NIP-23, type 30023, `docs/FORMATS.md` sections 4 bis et 4 quinquies), la lecture par l'application (`Article`, `VoirCatalog`, `Parcours`, `QuiDecide`…), le dossier `content/` et l'outil `tools/content-signer` pour les contenus d'exemple embarqués.

## État (8 novembre 2026)

- **Étape 1 faite** : `tools/content-signer/src/test/.../NoRegressionTest.kt` reconstruit, avec la clé publique lue dans les JSON embarqués, tous les paquets préparés par `ContentSigner.kt` (`fiches`, `revendications`, `parcours`, `comprendre`, `glossaire`, `fil`, `voir`, `organisations`, `appels`) et compare chaque événement (clé, date, type, étiquettes dans l'ordre, contenu, identifiant ; seule la signature est ignorée). `agenda.json` et `decider.json` sont signés dans les fichiers d'« Agir » et de « Décider » et ne sont pas couverts. Les cas fautifs de « Comprendre » sont dans `InvalidContentTest.kt` ; ceux de « Voir » viendront avec l'étape 2 bis.
- **Étape 2 faite** (D-092) : paquet `core.editorial.schema` (`ContentSchema.kt`, `EditorialEngine.kt`, `EditorialMessages.kt`, `EditorialSchemas.kt`, `ContentDocument.kt`) avec les ajouts demandés par « Voir » (rôle de lien libre, `TargetType.VOIR_BLOCK`, `ValueSpec.OfficialId(pattern)`, `FieldType.Number(decimals, allowNegative = false)` en écriture française, `FieldType.Hash`, `FieldType.Date(yearOnly)`, `CrossRule.UniqueBy`, `RequiredWhen` sur une position) ; schémas de « Comprendre » dans `core/comprendre/ComprendreSchemas.kt` ; l'outil appelle le moteur pour ces types (`tools/content-signer/.../ComprendreContent.kt`) ; les règles du glossaire entier (`GlossaryFormat.problems`) restent appliquées en plus. Écarts avec la section 1 : `Date(yearAllowed)` s'appelle `yearOnly` (accepte AAAA et AAAA-MM, comme avant) ; `Number(min, max, integer)` devient `Number(decimals, allowNegative, min, max)` ; `ContentSchema.duration` (le `ppf-duration` des parcours) ; `FieldSpec.silentValues` (« auto: oui » n'écrit pas d'étiquette) ; `ValueSpec.Link.kinds` (liens « parcours:… » du glossaire) ; `Positional.keepEmpty` ; `TargetType` gagne `COURSE`, `FACT_CHECK`, `QUI_DECIDE`, `STORY`, `GLOSSARY` ; `draftOf` prend un `CheckContext` facultatif (pour retrouver « fiche: » ou « voir: »).
- **Pour « Voir » (étape 2 bis)** : le moteur est prêt ; écrire `core/voir/VoirSchemas.kt`, l'ajouter à `EditorialSchemas.all`, puis remplacer `voirEvent`, `checkVoir` et `checkParty` en gardant le test de non-régression vert. À noter : le moteur ignore une ligne vide d'un champ répété (`voirEvent` en faisait une étiquette vide) et les étiquettes suivent l'ordre des entrées.
- **Étape 3 faite** (8 novembre 2026, D-093), branche `feature/comprendre` : point d'extension du socle `BackOfficeSection` (section 14 de `docs/integration/back-office.md`, à relire par le Directeur technique) ; migration `V11__editorial.sql` (numéro provisoire, renuméroté à l'entrée dans `main`) avec `editorial_draft` et `editorial_draft_version` ; magasin `server/.../editorial/EditorialDrafts.kt` (création, enregistrement avec concurrence optimiste, versions, purge à 180 jours) ; section `editorial.comprendre` (`EditorialDraftsSection.kt`) sous `/equipe/sections/editorial.comprendre` : liste des brouillons, nouveau brouillon par type, mise à jour d'un contenu publié, formulaire `EditorialForm.kt` sans JavaScript, aperçu (`web/.../comprendre/ComprendrePreview.kt`, même rendu que le site), différence ligne à ligne (`LineDiff.kt`), versions. Permission ajoutée : `editorial.<espace>.write` (rôle éditeur).
- Écarts avec les sections 2 et 4 : un seul brouillon ouvert **par identifiant**, quel que soit le type (l'adresse ne dépend pas du type) ; `slug` facultatif tant qu'il n'est pas valide (un brouillon s'enregistre toujours) et `title` gardé pour les listes ; `origin` tiré des champs de l'en-tête ; tables `editorial_review` et `editorial_signing` laissées aux étapes 4 et 5 ; les liens (`Link`, `Slug`) sont des champs texte avec la liste des identifiants connus (`datalist`, sans script) plutôt qu'une liste déroulante ; une liste sur une ligne (`InlineList`) est un seul champ ; les choix de moins de 8 valeurs sont des boutons radio ; « Prévisualiser » montre l'aperçu sous le formulaire sans enregistrer ; en cas de conflit, le formulaire revient avec le texte de l'éditeur, la différence avec la dernière version, et repart de cette version (enregistrer de nouveau garde le texte, en connaissance de cause). Clé éditoriale d'un espace en attendant l'étape 5 : celle qui a signé le plus de contenus publiés de l'espace (D-093).
- Relecture du socle par le Directeur technique (8 novembre 2026), changements faits : la version est écrite, puis la ligne du journal, dans le même gestionnaire ; si le journal refuse la ligne, l'éditeur reçoit une page d'erreur (jamais une page de réussite ni une redirection), la version restant enregistrée ; limite en caractères vérifiée par le serveur avant tout enregistrement (texte 60 000, une valeur de l'en-tête 5 000, tout le formulaire 75 000), annoncée dans l'aide du texte et posée en `maxlength`, bien avant la limite de 256 Kio de la section, comptée en octets encodés (une lettre accentuée en prend jusqu'à 6) ; plus aucune coupure silencieuse ; détails d'une ligne du journal d'une section bornés par le socle (20 au plus, clés `^[a-z0-9_]{1,40}$`, valeurs de 200 caractères au plus) ; tests : section absente du port public, envoi sans jeton ou d'une autre origine refusé avant le gestionnaire.
- **L'aperçu n'est jamais publiable** : l'événement non signé construit pour l'aperçu ne vit que le temps de la requête ; il est rendu en HTML (ni son identifiant, ni du JSON), jamais enregistré, exporté, transmis au port de publication ni au service de signature. Seule l'action de la file (étapes 4 et 5) construit l'événement à signer, de nouveau, à partir de la version relue.
- **Service de signature** (révision de D-129, dans `main` depuis `e017998`) : aucune cérémonie d'approbation dans notre section ; circuit de `PlatformSigner` décrit à la section 6 (réécrite le 8 novembre 2026).
- **Étape 4 faite** (8 novembre 2026, D-094) : migration `V12__editorial_queue.sql` (numéro provisoire) avec l'état `ready_to_sign` (à la place d'`approved`) et la table `editorial_review` (une décision par version, jamais modifiée, message ou motif libre ici seulement) ; magasin `EditorialReviews.kt` (soumission par une personne qui a écrit le brouillon, décisions, règle des deux personnes dans la transaction : ni l'auteur, ni une personne qui a écrit une version, ni un compte de même clé Nostr) ; file `EditorialQueue.kt` (`editorial.comprendre`, permission `queue.editorial.comprendre.review` : comptes, échéances et niveaux du tableau de bord, détail qui renvoie à la page de relecture ; pas d'action du socle) ; dans la section : bouton « Soumettre pour relecture » (seulement sans problème, vérifié de nouveau par le serveur), page `relecture/{id}` (contenu soumis rendu comme sur le site, échéance, décisions précédentes, formulaires « Demander des modifications », « Refuser », « Approuver pour publication »), état dans la liste des brouillons, message de la relecture sur la page du brouillon ; deux lignes du journal par action (`<action>_requested` puis `<action>` avec son résultat), identifiants et codes seulement ; point de branchement `EditorialPublisher.kt` (`handOver` après l'approbation, `state` à l'ouverture de la page, calqués sur `PlatformSigner`, section 6 ; lien « Approuver sur la page de signature » pour qui peut approuver ; par défaut : « service de signature pas encore branché »). Écarts avec la section 5 : décisions sur une page de la section plutôt que par les actions du socle (le socle garde le motif d'une action de file dans le journal) ; pas de `withdraw` ni de `retry_signing` (étape 5) ; pas de demande de validation à deux du socle (révision de D-129) ; règle « pas l'auteur » étendue à toute personne qui a écrit une version. Un brouillon publié, refusé ou retiré s'efface après 2 ans.
- Étapes 5 et 6 : à faire. Aucun schéma de « Comprendre » ne répète une clé (`Occurs.MANY`) : les lignes répétées sont testées sur un schéma de test ; elles serviront à « Voir » (étape 6).

## 0. Vue d'ensemble

```
éditeur A                    serveur principal                         service de signature        port de publication
---------                    -----------------                         --------------------        -------------------
formulaire (sans JS) --POST--> brouillon v1, v2… (editorial_draft_version)
« Soumettre »         -------> file editorial.<espace> (BackOfficeQueue, page « Relecture » de la section)
                               éditeur B (≠ A) : « Demander des modifications » / « Refuser » (motif)
                               éditeur B (≠ A) : « Approuver pour publication » -> prêt à signer
                                 currentKey(use), construit l'UnsignedEvent
                                 submitDraft(unsigned, use, double validation, auteur A) --> page d'approbation
                               lien « Approuver sur la page de signature » ------------> deux éditeurs ≠ A approuvent
                               à l'ouverture de la page : draftStatus(use, id) <--------- approbations
                                 quand prêt : request(unsigned, use), status(ticket) <--- Signed(event) / Refused
                                 PublicationPort.publish(event) ------------------------------------------> événements du serveur
                               journal d'audit (socle), journal public des corrections (événements)
```

Règles fixes :
1. **Un seul moteur** : Comprendre et Voir décrivent chaque type de contenu par un schéma déclaratif (section 1) ; le même moteur produit le formulaire, lit les réponses, valide et construit l'événement, côté serveur comme dans `tools/content-signer`.
2. **Deux personnes au moins** : l'auteur d'une version ne peut ni la relire ni approuver sa publication ; la publication passe par la validation à deux du service de signature (révision de D-129).
3. **La clé de la plateforme ne touche jamais le serveur principal** : il prépare l'événement non signé, le service de signature séparé le signe après avoir vérifié la preuve d'approbation.
4. **Une mise à jour est une nouvelle version de même adresse** (`30023:<clé éditoriale>:<slug>`), jamais une modification en place ; une correction de fond entre au journal public des corrections.
5. **Origine IA obligatoire** : le formulaire n'a pas de valeur par défaut pour « produit avec l'aide d'une IA » ; sans réponse explicite, la version n'est pas soumise.

## 1. Le schéma déclaratif (Kotlin commun, `shared/core`)

Paquet proposé : `org.politisationpopulairefrancaise.core.editorial.schema` (commonMain : ni `java.*`, ni réflexion, compatible iOS). Un schéma décrit **un type de contenu** : ses champs d'en-tête, la façon dont chacun devient une étiquette de l'événement, le corps et ses blocs, et ses règles. Les textes de l'interface ne sont pas dans le schéma : seulement des clés de traduction (`labelKey`, `helpKey`), résolues par les textes externalisés du serveur et de l'application.

### 1.1 Classes

```kotlin
/** The spaces that own an editorial queue (`editorial.<id>`). */
enum class EditorialSpace(val id: String) { COMPRENDRE("comprendre"), VOIR("voir") }

/** One type of editorial content, as the form, the checks and the event builder see it. */
data class ContentSchema(
    val type: ArticleType,                 // ppf-type of the event (existing enum, values never renamed)
    val space: EditorialSpace,
    val labelKey: String,                  // "editorial.type.qui_decide"
    val sourceDirectory: String,           // folder of content/ read by the tool: "qui-decide", "voir/faits"
    val bundle: String,                    // bundled JSON written by the tool: "comprendre.json", "voir.json"
    val summary: Presence = Presence.REQUIRED,    // common fields of Article.build
    val topics: Presence = Presence.OPTIONAL,
    val fields: List<FieldSpec>,           // the specific fields, in the order of their tags
    val body: BodySpec,
    val rules: List<CrossRule> = emptyList(),     // declarative cross-field rules
    val checks: List<SchemaCheck> = emptyList(),  // Kotlin extension point (1.4)
    val tagging: Tagging = Tagging.PPF_PREFIX,    // how a field becomes a tag (1.3)
)

enum class Presence { REQUIRED, OPTIONAL, ABSENT }

/** A field of the front matter (one key) and of the form (one fieldset). */
data class FieldSpec(
    val key: String,                       // front matter key, also the form name: "fact-status"
    val labelKey: String,
    val helpKey: String? = null,
    val occurs: Occurs = Occurs.ONE,       // how many times the key may appear
    val value: ValueSpec,
    val tag: String? = null,               // tag name when it is not "ppf-<key>" (Tagging.PPF_PREFIX)
)

/** Repetition of a key: ONE (exactly one), OPTIONAL (zero or one), MANY(min, max) (repeated key, one tag each). */
sealed interface Occurs {
    data object ONE : Occurs
    data object OPTIONAL : Occurs
    data class MANY(val min: Int = 0, val max: Int = 200) : Occurs
}

/** What one occurrence holds. */
sealed interface ValueSpec {
    /** A single value. */
    data class Single(val type: FieldType) : ValueSpec

    /**
     * Several values separated by « | » (the convention of content/ since D-034), one type per position.
     * An empty position (« a || c », or a trailing « | ») means « unknown » and is allowed only when the
     * position is optional; positions after the last given one may be left out when all are optional.
     */
    data class Positional(val positions: List<Position>) : ValueSpec

    /** A link to another content by its slug: becomes `["a", "30023:<key>:<slug>", "", role]`. */
    data class Link(val role: String, val target: TargetType) : ValueSpec

    /** « label | fiche:<slug> » or « label | voir:<slug> » or « label | espace:<id> » (ppf-next). */
    data object NextStep : ValueSpec

    /** Several values on one line separated by [separator] (« persons: a, b » ; « claims: x ; y »). */
    data class InlineList(val separator: String, val item: FieldType, val each: Tagging.Each) : ValueSpec
}

data class Position(val name: String, val labelKey: String, val type: FieldType, val required: Boolean = true)

/** The type of one value; each type has its parser, its check and its form control. */
sealed interface FieldType {
    data class Text(val maxLength: Int = 300, val multiline: Boolean = false) : FieldType
    /** A closed list of values (the tag values of an existing enum: FactKind, FactStatus, ProcedureStage…). */
    data class Choice(val values: List<String>, val labelKeyPrefix: String) : FieldType
    /** AAAA-MM-JJ ([yearAllowed]: AAAA too, as the `period` of a story). */
    data class Date(val yearAllowed: Boolean = false) : FieldType
    /** A number written the French way (« 4 210 345,60 »), read by Indicator.parseNumber. */
    data class Number(val min: Double? = null, val max: Double? = null, val integer: Boolean = false) : FieldType
    /** An https address (never followed by the server; shown as text in the back-office). */
    data object Url : FieldType
    data class Slug(val target: TargetType? = null) : FieldType
    data object Minutes : FieldType
}

/**
 * The expected type of a linked content. [prefix] is the stable name written in the schemas and in the
 * messages (« person: », « party: »); [types] the ArticleType the target must have, checked against the
 * published contents and the other drafts of the same batch.
 */
enum class TargetType(val prefix: String, val types: Set<ArticleType>) {
    PERSON("person", setOf(ArticleType.PERSONNE)),
    PARTY("party", setOf(ArticleType.PARTI)),
    TEXT("text", setOf(ArticleType.TEXTE)),
    INDICATOR("indicator", setOf(ArticleType.INDICATEUR)),
    FACT("fact", setOf(ArticleType.FAIT)),
    GUIDE("fiche", setOf(ArticleType.FICHE_PRATIQUE, ArticleType.DOSSIER, ArticleType.DECRYPTAGE, ArticleType.ARTICLE)),
    ANY("voir", emptySet()),               // any content (empty set: no type check, the slug must exist)
}

/** How a field becomes tags. */
enum class Tagging {
    /** `["ppf-<key>", value…]` (Voir, D-034), or `[tag, value…]` when FieldSpec.tag is given. */
    PPF_PREFIX;

    /** For an InlineList: one tag per item, or one tag holding all the items. */
    enum class Each { TAG_PER_ITEM, ONE_TAG }
}

/** The body: Markdown with fenced blocks « ::: name … ::: » and required sections. */
data class BodySpec(
    val blocks: List<BlockSpec>,
    val requiredSections: List<String> = listOf("Sources"),   // « ## Sources » for every type of Comprendre
    val templateKey: String? = null,       // text proposed in an empty draft (blocks already written)
    val maxLength: Int = 60_000,
)

data class BlockSpec(
    val name: String,                      // "quiz", "decideurs", "objections", "chronologie", "en-bref", "et-maintenant"
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val parser: BlockParser,               // the existing parser of the block, which returns its problems
)

/** The parsers already in core, wrapped: ContentBlocks (objections, chronologie), ParcoursFormat, QuiDecide. */
fun interface BlockParser { fun problems(lines: List<String>): List<String> }

/** Declarative rules between fields, checked after each field is valid on its own. */
sealed interface CrossRule {
    /** [field] is required when [whenField] has one of [values]. Ex.: a « affaire » fact needs its procedure. */
    data class RequiredWhen(val field: String, val whenField: String, val values: Set<String>, val messageKey: String) : CrossRule

    /** [field] is forbidden when [whenField] has one of [values]. */
    data class ForbiddenWhen(val field: String, val whenField: String, val values: Set<String>, val messageKey: String) : CrossRule

    /** Position [position] of [field] must be empty when its position [whenPosition] has one of [values]. */
    data class PositionEmptyWhen(val field: String, val position: String, val whenPosition: String, val values: Set<String>, val messageKey: String) : CrossRule

    /** No two occurrences of [field] with the same value at [position] (one year of public aid once). */
    data class UniquePosition(val field: String, val position: String, val messageKey: String) : CrossRule

    /** At least one of [fields] is present. */
    data class AnyOf(val fields: List<String>, val messageKey: String) : CrossRule

    /** The body has the block [block] when [whenField] has one of [values]. */
    data class BlockRequiredWhen(val block: String, val whenField: String, val values: Set<String>, val messageKey: String) : CrossRule
}

/**
 * Extension point for a rule that does not describe itself: receives the draft, the event it gives and the
 * context (published contents, other drafts of the batch), returns problems. Pure function, common code.
 */
fun interface SchemaCheck { fun problems(draft: ContentDraft, event: UnsignedEvent, context: CheckContext): List<Problem> }

/** What a check may know: the type of every known address, and the slugs of the guides. */
interface CheckContext {
    fun typeOf(address: ContentAddress): ArticleType?
    val authorHex: String
}

/** A draft as the engine reads it: the ordered entries of the front matter (repeated keys kept) and the body. */
data class ContentDraft(val type: ArticleType, val entries: List<Pair<String, String>>, val body: String)

/** A problem, located for the form: field key, occurrence (0-based), position name; [code] is a translation key. */
data class Problem(val code: String, val field: String? = null, val occurrence: Int? = null, val position: String? = null, val args: List<String> = emptyList())
```

Les champs communs à tout article (`slug`, `title`, `summary`, `topics`, `origin`, `published_at`, `updated_at`) ne sont pas dans `fields` : le moteur les traite toujours de la même façon, dans l'ordre de `Article.build`. `origin` est un choix obligatoire sans valeur par défaut (règle fixe 5).

### 1.2 Exemple complet pour Comprendre : `qui-decide`

```kotlin
val QUI_DECIDE = ContentSchema(
    type = ArticleType.QUI_DECIDE,
    space = EditorialSpace.COMPRENDRE,
    labelKey = "editorial.type.qui_decide",
    sourceDirectory = "qui-decide",
    bundle = "comprendre.json",
    fields = listOf(
        // « persons: alex-modele, sacha-exemple » -> one ["a", "30023:<key>:<slug>", "", "personne"] per slug
        FieldSpec(
            key = "persons",
            labelKey = "editorial.field.persons",
            helpKey = "editorial.help.persons",
            occurs = Occurs.OPTIONAL,
            value = ValueSpec.InlineList(",", FieldType.Slug(TargetType.PERSON), Tagging.Each.TAG_PER_ITEM),
            tag = "a",   // with role ComprendreTags.PERSON_LINK_ROLE, as today
        ),
    ),
    body = BodySpec(
        blocks = listOf(
            BlockSpec("decideurs", min = 1, max = 1, parser = QuiDecideBlocks.deciders),   // QuiDecide.parse
            BlockSpec("en-bref", max = 1, parser = BlockParser { emptyList() }),
            BlockSpec("et-maintenant", min = 1, max = 1, parser = BlockParser { emptyList() }),
            BlockSpec("objections", parser = ContentBlockParsers.objections),             // ContentBlocks
            BlockSpec("chronologie", parser = ContentBlockParsers.timeline),
        ),
        requiredSections = listOf("Sources"),
        templateKey = "editorial.template.qui_decide",
    ),
    checks = listOf(QuiDecideChecks.readable),  // QuiDecide.parse(body).problems, as ContentSigner today
)
```

Le parcours suit le même modèle : champ `duration` (`FieldType.Minutes`, `Occurs.ONE`, porté par `Article.build` en `ppf-duration`) ; blocs `en-bref` (1), `etape` (au moins 1, chacune avec son `quiz`), `et-maintenant` (1) ; contrôle `ParcoursFormat.parse(body).problems`. Le décryptage : `verdict` (`Choice` des valeurs de `Verdict`, étiquette `ppf-verdict`), `claim` (texte, `ppf-claim`), `claim_context` (facultatif) ; bloc `objections` au moins 1. L'histoire des luttes : `period` (`Positional` : début date-ou-année obligatoire, fin facultative), `country`, `claims` et `results` (`InlineList` « ; », une étiquette par élément) ; bloc `objections` au moins 1.

### 1.3 Exemples complets pour Voir : `fait` et `parti`

Les noms de champs, d'étiquettes et de valeurs sont ceux de `VoirTags` et des fichiers de `content/voir/` ; ils ne changent pas.

```kotlin
val SOURCE = FieldSpec(  // « source: justice | Jugement… | https://… | 2025-12-10 », shared by every type of Voir
    key = "source", labelKey = "voir.field.source", occurs = Occurs.MANY(min = 1),
    value = ValueSpec.Positional(listOf(
        Position("kind", "voir.source.kind", FieldType.Choice(SourceKind.tagValues, "voir.source.kind.")),
        Position("title", "voir.source.title", FieldType.Text(300)),
        Position("url", "voir.source.url", FieldType.Url),
        Position("date", "voir.source.date", FieldType.Date(), required = false),
    )),
)
val CORRECTION = FieldSpec(  // « correction: 2026-06-05 | Le statut indiquait par erreur… » (journal public, section 3.4)
    key = "correction", labelKey = "voir.field.correction", occurs = Occurs.MANY(),
    value = ValueSpec.Positional(listOf(Position("date", "voir.correction.date", FieldType.Date()), Position("text", "voir.correction.text", FieldType.Text(1000, multiline = true)))),
)
val NEXT = FieldSpec(key = "next", labelKey = "voir.field.next", occurs = Occurs.MANY(), value = ValueSpec.NextStep)

val FAIT = ContentSchema(
    type = ArticleType.FAIT,
    space = EditorialSpace.VOIR,
    labelKey = "editorial.type.fait",
    sourceDirectory = "voir/faits",
    bundle = "voir.json",
    summary = Presence.OPTIONAL,
    fields = listOf(
        FieldSpec("fact", "voir.field.fact", value = ValueSpec.Single(FieldType.Choice(FactKind.tagValues, "voir.fact."))),
        FieldSpec("date", "voir.field.date", value = ValueSpec.Single(FieldType.Date())),
        FieldSpec("place", "voir.field.place", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(120))),
        FieldSpec("fact-status", "voir.field.fact_status", value = ValueSpec.Single(FieldType.Choice(FactStatus.tagValues, "voir.fact_status."))),
        FieldSpec("procedure", "voir.field.procedure", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(ProcedureStage.tagValues, "voir.procedure."))),
        FieldSpec("sentence", "voir.field.sentence", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(300))),
        FieldSpec("sentence-served", "voir.field.sentence_served", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(300))),
        FieldSpec("promise", "voir.field.promise", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(PromiseStatus.tagValues, "voir.promise."))),
        FieldSpec("vote", "voir.field.vote", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(VotePosition.tagValues, "voir.vote."))),
        FieldSpec("quote", "voir.field.quote", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(1000, multiline = true))),
        FieldSpec("liberty", "voir.field.liberty", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(LibertyCategory.tagValues, "voir.liberty."))),
        FieldSpec("recourse", "voir.field.recourse", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(RecourseStatus.tagValues, "voir.recourse."))),
        // « person: alex-modele » -> ["a", "30023:<key>:alex-modele", "", "personne"], the target must be a person
        FieldSpec("person", "voir.field.person", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_PERSON, TargetType.PERSON)),
        FieldSpec("text", "voir.field.text", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_TEXT, TargetType.TEXT)),
        SOURCE,
        FieldSpec("archive", "voir.field.archive", occurs = Occurs.MANY(), value = ValueSpec.Positional(listOf(
            Position("url", "voir.archive.url", FieldType.Url), Position("date", "voir.archive.date", FieldType.Date(), required = false)))),
        CORRECTION,
        NEXT,
    ),
    body = BodySpec(blocks = emptyList(), requiredSections = emptyList()),   // the sources are tags in Voir
    rules = listOf(
        // A case is never shown without the stage of its procedure (presumption of innocence, Fact.from).
        CrossRule.RequiredWhen("procedure", whenField = "fact", values = setOf("affaire"), messageKey = "voir.rule.case_needs_procedure"),
        CrossRule.ForbiddenWhen("sentence", whenField = "fact", values = FactKind.tagValues.toSet() - "affaire", messageKey = "voir.rule.sentence_only_for_cases"),
    ),
    checks = listOf(VoirChecks.readableByCatalog),   // Fact.from(Article.from(event)) != null, as checkVoir today
)

val PARTI = ContentSchema(
    type = ArticleType.PARTI,
    space = EditorialSpace.VOIR,
    labelKey = "editorial.type.parti",
    sourceDirectory = "voir/partis",
    bundle = "voir.json",
    fields = listOf(
        FieldSpec("acronym", "voir.field.acronym", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(20))),
        FieldSpec("founded", "voir.field.founded", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Date())),
        // « financing: association-de-financement | Agrément CNCCFP n° … | 2009-07-02 | texte-officiel | Journal officiel… | https://… »
        FieldSpec("financing", "voir.field.financing", occurs = Occurs.ONE, value = ValueSpec.Positional(listOf(
            Position("kind", "voir.financing.kind", FieldType.Choice(listOf("association-de-financement", "mandataire-financier"), "voir.financing.kind.")),
            Position("reference", "voir.financing.reference", FieldType.Text(200), required = false),
            Position("since", "voir.financing.since", FieldType.Date(), required = false),
            Position("source-kind", "voir.source.kind", FieldType.Choice(SourceKind.tagValues, "voir.source.kind."), required = false),
            Position("source-title", "voir.source.title", FieldType.Text(300), required = false),
            Position("source-url", "voir.source.url", FieldType.Url, required = false),
        ))),
        // « public-funding: 2026 | 4 210 345,60 | Décret… | https://… »
        FieldSpec("public-funding", "voir.field.public_funding", occurs = Occurs.MANY(), value = ValueSpec.Positional(listOf(
            Position("year", "voir.public_funding.year", FieldType.Number(min = 1988.0, max = 2100.0, integer = true)),
            Position("amount", "voir.public_funding.amount", FieldType.Number(min = 0.0)),
            Position("decree", "voir.public_funding.decree", FieldType.Text(300)),
            Position("url", "voir.public_funding.url", FieldType.Url),
        ))),
        FieldSpec("european-party", "voir.field.european_party", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(200))),
        FieldSpec("european-group", "voir.field.european_group", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(200))),
        // « seats: assemblee | 120 | 2026-10-01 | parlement | Assemblée nationale… | https://… »
        FieldSpec("seats", "voir.field.seats", occurs = Occurs.MANY(), value = ValueSpec.Positional(listOf(
            Position("chamber", "voir.seats.chamber", FieldType.Choice(listOf("assemblee", "senat", "parlement-europeen", "autre"), "voir.seats.chamber.")),
            Position("count", "voir.seats.count", FieldType.Number(min = 0.0, integer = true)),
            Position("date", "voir.seats.date", FieldType.Date()),
            Position("source-kind", "voir.source.kind", FieldType.Choice(SourceKind.tagValues, "voir.source.kind.")),
            Position("source-title", "voir.source.title", FieldType.Text(300)),
            Position("source-url", "voir.source.url", FieldType.Url),
        ))),
        FieldSpec("leader", "voir.field.leader", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_LEADER, TargetType.PERSON)),
        FieldSpec("website", "voir.field.website", occurs = Occurs.MANY(), value = ValueSpec.Single(FieldType.Url)),
        SOURCE, CORRECTION, NEXT,
    ),
    body = BodySpec(blocks = emptyList(), requiredSections = emptyList()),
    rules = listOf(
        // Exactly one financing structure: Occurs.ONE. Never the name of a financial agent (a private person).
        CrossRule.PositionEmptyWhen("financing", position = "reference", whenPosition = "kind", values = setOf("mandataire-financier"), messageKey = "voir.rule.no_agent_name"),
        CrossRule.UniquePosition("public-funding", position = "year", messageKey = "voir.rule.public_funding_year_once"),
    ),
    checks = listOf(VoirChecks.readableByCatalog),   // Party.from(...) != null
)
```

Ce que « Voir » a demandé, et où c'est couvert :

| Demande de « Voir » | Dans le schéma |
|---|---|
| Champs répétés, valeurs séparées par « \| », un type par position (mesures, sources, sièges, aide publique) | `Occurs.MANY` + `ValueSpec.Positional` ; positions facultatives = « inconnu » ; `measure` d'un indicateur se décrit pareil (identifiant, date, nombre, type de source, titre, adresse, fiabilité, puis occasion, lieu, part facultatifs) |
| Liens par slug avec rôle et type de cible attendu (`person:` vers une personne, `party:` vers un parti) | `ValueSpec.Link(role, TargetType)` ; le moteur vérifie que la cible existe (contenus publiés et brouillons du même lot) et a le bon `ArticleType` |
| Un fait « affaire » exige l'étape de la procédure | `CrossRule.RequiredWhen` |
| Un parti exige une seule structure de financement, sans nom de mandataire | `Occurs.ONE` + `CrossRule.PositionEmptyWhen` |
| Règles qui ne se décrivent pas | `SchemaCheck` (fonction Kotlin commune) ; exemple : « l'application lirait-elle ce bloc ? » (`VoirCatalog`) |
| Blocs du corps `::: quiz`, `::: decideurs`, `::: objections`, `::: chronologie`, `## Sources` obligatoire | `BodySpec.blocks` (parseurs existants) et `requiredSections` |

### 1.4 Le moteur (`EditorialEngine`, commun)

```kotlin
object EditorialEngine {
    /** Every problem of the draft: each field alone, then the cross rules, then the body, then the checks. */
    fun check(schema: ContentSchema, draft: ContentDraft, context: CheckContext): List<Problem>
    /** The unsigned event, exactly as ContentSigner builds it today (section 3). */
    fun build(schema: ContentSchema, draft: ContentDraft, author: PublicKey, createdAt: Long? = null): UnsignedEvent
    /** A published event read back as a draft (for an update): entries in the order of the tags. */
    fun draftOf(schema: ContentSchema, event: SignedEvent): ContentDraft
}
object EditorialSchemas { val all: List<ContentSchema>; fun of(type: ArticleType): ContentSchema? }
```

`ComprendreSchemas` et `VoirSchemas` déclarent chacun leurs schémas dans leurs propres fichiers (`core/comprendre/ComprendreSchemas.kt`, `core/voir/VoirSchemas.kt`, ce dernier écrit par « Voir ») ; `EditorialSchemas.all` les réunit. Un type nouveau = un schéma nouveau, sans toucher au moteur.

## 2. Le formulaire kotlinx.html sans JavaScript et la validation côté serveur

- **Rendu** (`server/.../editorial/EditorialForm.kt`) : un `fieldset` par `FieldSpec`, une `legend` (libellé), l'aide reliée par `aria-describedby`. Contrôle par type : `Text` → `input type=text` (ou `textarea`), avec `maxlength` ; `Choice` → `select` (moins de 8 valeurs : boutons radio) ; `Date` → `input type=date` (et texte libre `AAAA` si `yearAllowed`) ; `Number` → `input type=text inputmode=decimal` (écriture française acceptée) ; `Url` → `input type=url` ; `Link` et `Slug(target)` → `select` des contenus publiés du type attendu (titre et slug), plus un champ texte pour un slug d'un brouillon du même lot ; `Positional` → une ligne de contrôles, un par position, avec leurs libellés.
- **Champs répétés** sans JavaScript : le formulaire montre les occurrences existantes et **deux lignes vides** ; le bouton « Ajouter des lignes » (`<button name="action" value="more:<key>">`) renvoie le formulaire avec deux lignes de plus, sans rien perdre ; une ligne entièrement vide est ignorée. Retirer = vider la ligne. Monter ou descendre une ligne : boutons `move:<key>:<i>:up|down`.
- **Noms des contrôles** : `f.<key>.<occurrence>.<position>` (par exemple `f.seats.2.count`), `body`, `origin`, champs communs `c.title`… Le serveur reconstruit `ContentDraft.entries` dans l'ordre du schéma, chaque occurrence en `valeur | valeur | …` (positions vides finales retirées), exactement comme une ligne de `content/`.
- **Corps** : une `textarea` (Markdown) ; un brouillon neuf reçoit le modèle (`templateKey`) avec les blocs attendus déjà écrits ; l'aide liste les blocs et leur syntaxe ; le bouton « Prévisualiser » rend le contenu comme sur le site (même rendu que `web/`), sans JavaScript.
- **Origine IA** : deux boutons radio « Oui, avec l'aide d'une IA » / « Non », aucun coché par défaut, `required` ; sans réponse, la version est enregistrée comme brouillon mais ne peut pas être soumise.
- **Validation côté serveur, toujours** (le navigateur ne fait que des contrôles de confort) : `EditorialEngine.check` à chaque enregistrement ; les problèmes s'affichent en tête (résumé avec `role=alert` et liens vers les champs) et à côté de chaque champ (`aria-invalid`, message relié). Un brouillon invalide s'enregistre (on ne perd jamais le travail) mais ne se soumet pas.
- **Sécurité** : pages du socle (port 8081, session, jeton anti-falsification de chaque formulaire, `Cache-Control: no-store`, CSP sans `unsafe-inline`) ; taille maximale du formulaire (256 Ko) ; aucun HTML accepté dans les champs (le Markdown du corps est rendu par le moteur du site, qui échappe le HTML) ; les adresses sont affichées en texte dans le back-office, jamais suivies.
- **Concurrence** : chaque formulaire porte le numéro de la version de départ ; enregistrer sur une version qui n'est plus la dernière renvoie le formulaire avec « Une autre version a été enregistrée entre-temps » et la différence, sans écraser.

## 3. Déplacer les vérifications vers `shared/core` sans changer un octet

### 3.1 Ce qui part de `tools/content-signer`

| Aujourd'hui (`ContentSigner.kt`) | Demain (`shared/core`, commonMain) | Qui |
|---|---|---|
| `Document`, `parseDocument` | `core/editorial/source/ContentDocument.kt` (même lecture : en-tête `---`, commentaires `#`, clés répétées gardées dans l'ordre, corps `trim() + "\n"`) | Comprendre |
| `dateToEpochSeconds` (`java.time`, midi UTC) | `core/editorial/source/EditorialDates.kt`, sur `CivilDate` (calcul des jours sans `java.time`) | Comprendre |
| fiches, revendications, parcours, `comprendreEvents` (décryptages, histoire, qui-décide, méthode), `glossaryEvents` | `ComprendreSchemas` + `EditorialEngine.build` ; `GlossaryFormat` reste tel quel (contrôle d'ensemble) | Comprendre |
| `voirEvent`, `checkVoir`, `checkParty`, `LINK_KEYS`, `COMMON_KEYS` | `VoirSchemas` | **Voir** le déplace lui-même |
| `checkUniqueAddresses` | `core/editorial/source/AddressCollisions.kt` (sur une liste d'événements) | Comprendre |
| `callOf` (appels), `Organisation.build`, `AgendaContent.kt`, `DeciderContent.kt` | inchangés (hors circuit éditorial pour l'instant) | Agir, Décider |

L'outil garde : parcours des dossiers, clé de signature, signature, écriture des JSON, messages. Il appelle `EditorialEngine.check` puis `build` pour chaque fichier, avec le schéma du dossier (`sourceDirectory`).

### 3.2 Règles pour ne rien changer aux octets

- Le moteur construit les étiquettes **dans l'ordre des entrées du fichier** (comme `voirEvent` aujourd'hui), pas dans l'ordre du schéma : l'ordre du schéma ne sert qu'au formulaire.
- Mêmes conversions : liens `["a", "30023:<clé>:<slug>", "", rôle]`, `next` (`fiche:` et `voir:` deviennent une adresse, `espace:` devient `space:<id>`), champs séparés par `|` avec `trim()`, champ vide gardé (« inconnu »), listes `persons` (virgule) et `claims`/`results` (« ; » puis « ; »), `period` sans les positions vides.
- Mêmes dates : `published_at` et `created_at` à midi UTC de la date du fichier.
- Les messages d'erreur peuvent changer (ils passent par des clés de traduction) ; les événements, jamais.

### 3.3 Test de non-régression (écrit **avant** le déplacement)

`tools/content-signer/src/test/.../NoRegressionTest.kt` :
1. lit les JSON embarqués (`shared/ui/src/commonMain/composeResources/files/contents/*.json`) et en tire la clé publique de l'auteur (`pubkey`) ;
2. reconstruit tous les événements non signés à partir de `content/` avec **cette clé publique** (construire un événement ne demande pas la clé secrète : le test tourne en intégration continue sans secret) ;
3. compare, fichier par fichier et dans l'ordre, chaque événement à celui du JSON embarqué : `id`, `pubkey`, `created_at`, `kind`, `tags`, `content` identiques ; seule `sig` est ignorée (BIP-340 avec aléa) ; comme `id` est l'empreinte de tout le reste, c'est une égalité à l'octet près ;
4. vérifie aussi que l'outil refuse toujours les fichiers fautifs connus (jeu de cas dans `src/test/resources/invalid/` : affaire sans procédure, parti à deux financements, mandataire nommé, lien `person:` vers un parti, parcours sans quiz, contenu de Comprendre sans `## Sources`, deux contenus de même adresse).

Le test passe sur `main` avant tout changement, puis à chaque étape du déplacement (Comprendre d'abord, Voir ensuite). Les JSON embarqués ne sont pas régénérés pendant le déplacement.

### 3.4 Mise à jour et journal public des corrections

- Une mise à jour garde le slug, donc l'adresse ; `published_at` reste celui de la première version ; `created_at` = l'instant de la publication, et au moins `created_at` de la version publiée + 1 (deux versions le même jour ne doivent jamais avoir la même date : à date égale, NIP-01 garde le plus petit `id`, pas le plus récent).
- Le formulaire de mise à jour demande la nature du changement : « Correction » (une erreur de fond : un fait, un chiffre, un statut) ou « Mise à jour » (complément, nouvelle source, forme). Une correction **ajoute** une ligne `correction: <date> | <texte>` (étiquette `ppf-correction`, déjà lue par « Voir ») aux corrections des versions précédentes, qui ne peuvent pas être retirées ; Comprendre adopte la même étiquette (ajout au format, section 4 bis de `docs/FORMATS.md`, à écrire au moment du code).
- Le **journal public des corrections** est une page du site (`/corrections`, sans JavaScript) et un flux JSON qui listent les étiquettes `ppf-correction` des derniers événements de chaque adresse, par date : aucune table de plus, tout est dans les événements signés.

## 4. Tables et migration (`V10__editorial.sql`, numéro provisoire)

```sql
-- One editorial content being written or updated (one row per draft, whatever its versions).
CREATE TABLE editorial_draft (
    id                 UUID        PRIMARY KEY,
    space              TEXT        NOT NULL CHECK (space IN ('comprendre', 'voir')),
    type               TEXT        NOT NULL,                 -- ppf-type (ArticleType.tagValue)
    slug               TEXT        NOT NULL CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,99}$'),
    -- The published event this draft updates (null: a new content).
    base_event_id      TEXT        CHECK (base_event_id IS NULL OR base_event_id ~ '^[0-9a-f]{64}$'),
    status             TEXT        NOT NULL CHECK (status IN ('draft', 'in_review', 'changes_requested', 'approved', 'signing', 'published', 'rejected', 'withdrawn', 'signing_refused')),
    author_account     UUID        NOT NULL REFERENCES team_account (id),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    latest_version     INTEGER     NOT NULL CHECK (latest_version >= 1),
    submitted_version  INTEGER,                              -- the version in the queue, if any
    submitted_at       TIMESTAMPTZ,
    published_event_id TEXT        CHECK (published_event_id IS NULL OR published_event_id ~ '^[0-9a-f]{64}$')
);
-- One open draft per address (a new content or an update of the same slug).
CREATE UNIQUE INDEX editorial_draft_open_slug_idx ON editorial_draft (type, slug) WHERE status IN ('draft', 'in_review', 'changes_requested', 'approved', 'signing', 'signing_refused');

-- The successive versions of a draft: never modified (trigger), deleted only with a never submitted draft.
CREATE TABLE editorial_draft_version (
    draft_id     UUID        NOT NULL REFERENCES editorial_draft (id) ON DELETE CASCADE,
    version      INTEGER     NOT NULL CHECK (version >= 1),
    entries      JSONB       NOT NULL,      -- ordered [[key, value], ...] of the front matter, repeated keys kept
    body         TEXT        NOT NULL CHECK (length(body) <= 60000),
    origin       TEXT        CHECK (origin IS NULL OR origin IN ('human', 'ai-assisted')),   -- null: not answered yet
    change_kind  TEXT        CHECK (change_kind IS NULL OR change_kind IN ('correction', 'mise-a-jour')),
    change_note  TEXT        CHECK (change_note IS NULL OR length(change_note) <= 1000),
    written_by   UUID        NOT NULL REFERENCES team_account (id),
    written_at   TIMESTAMPTZ NOT NULL,
    problems     INTEGER     NOT NULL CHECK (problems >= 0),   -- count of EditorialEngine.check problems when saved
    PRIMARY KEY (draft_id, version)
);

-- Each review decision (the journal of the base holds the same, this table serves the pages).
CREATE TABLE editorial_review (
    draft_id    UUID        NOT NULL,
    version     INTEGER     NOT NULL,
    reviewer    UUID        NOT NULL REFERENCES team_account (id),
    decision    TEXT        NOT NULL CHECK (decision IN ('changes_requested', 'rejected', 'approved')),
    reason      TEXT        CHECK (reason IS NULL OR length(reason) <= 2000),
    decided_at  TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (draft_id, version) REFERENCES editorial_draft_version (draft_id, version) ON DELETE CASCADE
);

-- A signing request sent to the separate signing service, and what became of it.
CREATE TABLE editorial_signing (
    draft_id        UUID        NOT NULL,
    version         INTEGER     NOT NULL,
    ticket          TEXT        NOT NULL UNIQUE,
    unsigned_id     TEXT        NOT NULL CHECK (unsigned_id ~ '^[0-9a-f]{64}$'),
    unsigned_event  JSONB       NOT NULL,
    approval_id     UUID        NOT NULL,          -- team_pending_approval
    requested_at    TIMESTAMPTZ NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('pending', 'signed', 'refused', 'published', 'publish_failed')),
    signed_event_id TEXT,
    refusal         TEXT        CHECK (refusal IS NULL OR length(refusal) <= 500),
    checked_at      TIMESTAMPTZ,
    FOREIGN KEY (draft_id, version) REFERENCES editorial_draft_version (draft_id, version)
);
```

Données (à reporter dans `docs/REGISTRE_DONNEES.md`) : textes éditoriaux de l'équipe et identifiants internes des comptes de l'équipe (pseudonymes) ; aucune donnée de citoyen. Durée : un brouillon jamais soumis s'efface après 180 jours sans modification (tâche quotidienne) ; un brouillon publié, refusé ou retiré garde ses versions 2 ans (traçabilité de la relecture), puis seules restent les lignes du journal d'audit et les événements publiés. Le serveur ne garde jamais l'adresse IP ni le navigateur.

## 5. La file `editorial.<espace>` (`BackOfficeQueue`)

Une classe, deux enregistrements : `EditorialQueue(EditorialSpace.COMPRENDRE)` (`editorial.comprendre`) et `EditorialQueue(EditorialSpace.VOIR)` (`editorial.voir`), « Voir » n'ayant qu'à l'enregistrer.

- `viewPermission` = `queue.editorial.<espace>.review` (déjà dans le rôle `editor`).
- **Éléments** : les brouillons `in_review`, `approved`, `signing`, `signing_refused` ; identifiant `"<uuid du brouillon>:v<version>"` : une nouvelle version change l'identifiant, et une demande de validation à deux portant sur l'ancien devient sans objet (le socle la marque `failed`, car `actions` ne propose plus rien pour l'ancien identifiant).
- **Détail** (`render`) : le contenu rendu comme sur le site ; la différence avec la version publiée (champ par champ pour l'en-tête, ligne à ligne pour le corps) ; les sources en texte ; l'origine (IA ou non) ; la nature du changement et la note de correction ; l'auteur (pseudonyme) ; les décisions précédentes et leurs motifs ; les problèmes du moteur s'il y en a (une version à problèmes ne peut pas être soumise, mais les règles peuvent avoir changé depuis).
- **Actions** :

| Action | Permission | Motif | Sensible | Validation à deux | Effet |
|---|---|---|---|---|---|
| `request_changes` | `.review` | obligatoire | non | non | retour à l'auteur (`changes_requested`) ; refusée si l'acteur est l'auteur de la version |
| `reject` | `.review` | obligatoire | oui | non | `rejected` ; refusée si l'acteur est l'auteur |
| `publish` | `.publish` | facultatif | oui | **oui** | demande créée par n'importe quel éditeur (en général l'auteur, depuis la file ou sa page de brouillon) ; au moment de l'approbation, `perform` refuse si l'approbateur est l'auteur de la version (le socle refuse déjà qu'il soit le demandeur) ; puis section 6 |
| `retry_signing` | `.publish` | non | oui | non | après `signing_refused` pour une raison passagère : renvoie la même demande |
| `withdraw` | `.review` | obligatoire | non | non | l'auteur ou un éditeur retire une version de la file (`withdrawn`) |

`actions(itemId)` ne connaît pas l'acteur (contrat 1.1) : la règle « pas l'auteur » est donc vérifiée dans `perform`, qui renvoie `Refused("Vous êtes l'auteur de cette version : un autre éditeur doit la relire.")`.

- **Échéances et niveaux** (S-115) : une correction (`change_kind = correction`) a le niveau 4 et une échéance de 48 heures après la soumission ; un contenu nouveau ou une mise à jour, niveau 2, échéance de 7 jours ; un élément en `signing_refused`, niveau 5, sans échéance. La demande de validation à deux expire après 7 jours (socle).
- `pendingCount` = éléments `in_review` et `signing_refused`.

### 5.4 Ce qu'il faut demander au socle (Directeur technique)

1. **Pages d'édition** : le contrat interdit aux pôles leurs propres pages d'administration ; le formulaire de brouillon en est une. Proposition : un point d'extension `BackOfficeWorkspace` (un pôle fournit des pages sous `/equipe/<espace>/…`, rendues dans la mise en page du socle, avec sa session, sa permission, son jeton anti-falsification et l'entrée du journal pour chaque enregistrement), ou bien une page « créer » fournie par la file (`BackOfficeQueue.forms`). À décider par le Directeur technique avant l'étape 3.
2. **Identité du demandeur** dans `perform` lors d'une validation à deux (facultatif : la règle « pas l'auteur » suffit sans elle, mais la preuve pour le service de signature en a besoin, section 6).
3. Accès en lecture à la clé Nostr facultative des comptes (`team_account`), si la preuve d'approbation la retient.

## 6. Branchement sur le service de signature (interface définitive, D-129)

L'interface est `PlatformSigner` (`server/.../signing/PlatformSigner.kt`, dans `main` depuis `e017998` ; `FakePlatformSigner` dans les sources de test du serveur), décrite par `docs/integration/service-signature.md`, section 5. Le serveur principal ne voit ni ne transporte jamais une approbation : deux éditeurs approuvent sur la page du service de signature lui-même, qui vérifie leurs clés d'accès. Le point de branchement côté file est `EditorialPublisher` (étape 4, `handOver` et `state`) ; l'étape 5 y branche ce circuit sans toucher au reste :

1. **Après l'approbation dans la file** (`handOver`) : `currentKey(use)` avec `use` = `SigningUse.EDITORIAL_COMPRENDRE` (ou `EDITORIAL_VOIR`) ; s'il répond `null`, la page dit « La signature n'est pas encore disponible » et rien n'est envoyé : un contenu n'est **jamais** publié sans signature. Sinon l'événement non signé est construit avec cette clé (`EditorialEngine.build`, `created_at` de la section 3.4, strictement après celui de la version publiée), puis `submitDraft(unsigned, use, doubleValidation = true, author = DraftAuthor(compte de l'auteur, clé Nostr de l'auteur))` répond `Submitted(draftId, approvalUrl)` ou `Refused(code, detail)`.
2. **Lien d'approbation** : `approvalUrl` est montrée sur la page de relecture comme un simple lien « Approuver sur la page de signature », aux seuls membres qui peuvent approuver (`queue.editorial.<espace>.publish`, et jamais une personne qui a écrit le brouillon) ; aucune cérémonie de clé sur notre serveur ; le lien n'est affiché que s'il est en `https://` (ou sur `localhost` en développement).
3. **Suivi, à l'ouverture de la page** (`state`, aucune boucle en arrière-plan) : `draftStatus(use, eventId)` donne les approbations enregistrées (`approvals`, `required`, `ready`) ; `null` si le service ne connaît pas le brouillon ou ne répond pas.
4. **Signature** : quand `ready`, `request(unsigned, use)` donne un ticket ; `status(ticket)` répond `Pending`, `Signed(event)` ou `Refused(code, detail)` (codes stables : `approvals_missing`, `unknown_draft`, `key_revoked`, `signer_unavailable`, `bad_signature_from_signer`, `untrusted_key_from_signer`, `unknown_ticket`…).
5. **Publication** : `Signed.event` est publié **tel quel** par le `PublicationPort` unique ; brouillon `published`, `published_event_id` renseigné ; une correction entre au journal public des corrections (section 3.4).
6. **Expiration** : un brouillon expire après 7 jours sur le service de signature ; il faut alors un nouvel événement, avec un nouveau `created_at`, soumis de nouveau.

À garder à l'étape 5 (sans rien de secret) : l'identifiant de l'événement soumis, l'adresse d'approbation et l'état de la demande (table `editorial_signing`), pour relire `draftStatus` à l'ouverture de la page.

- Le service n'accepte, pour ces usages, que les `kind` 30023 dont `ppf-type` n'appartient pas à un autre usage (`SigningUse.matches`).
- Sur `Refused` : état `signing_refused`, alerte sur le tableau de bord (`postAlert`, niveau 5), nouvelle soumission possible (étape 5).
- Tant que le service n'est pas branché, `UnpluggedEditorialPublisher` répond « Le service de signature n'est pas encore branché » ; les tests de l'étape 5 utiliseront `FakePlatformSigner`.

## 7. Étapes livrables et tests

| Étape | Contenu | Tests | Qui |
|---|---|---|---|
| 1. Filet | Test de non-régression de la section 3.3, sur `main`, sans rien déplacer | le test lui-même, en intégration continue | Comprendre |
| 2. Schéma et moteur | `core/editorial/schema` (classes de la section 1), `EditorialEngine.check`/`build`/`draftOf`, `ContentDocument`, `EditorialDates`, `AddressCollisions` ; schémas de Comprendre ; l'outil appelle le moteur pour Comprendre | tests communs (`commonTest`) par type de champ, par règle croisée et par bloc ; aller-retour `draftOf(build(d)) == d` ; non-régression verte | Comprendre |
| 2 bis. Voir | `VoirSchemas` (fait, parti, personne, indicateur, texte, mouvement, dossier, réponse), déplacement de `voirEvent`/`checkVoir`/`checkParty` | non-régression verte ; cas fautifs de Voir | Voir |
| 3. Brouillons | `V10__editorial.sql`, magasin des brouillons et des versions, formulaire sans JavaScript, prévisualisation, différence avec la version publiée ; dépend du point 5.4.1 | intégration sur PostgreSQL embarqué ; formulaire au clavier, lecteur d'écran (libellés, erreurs reliées) ; champs répétés sans JavaScript ; concurrence de deux versions | Comprendre |
| 4. File | `EditorialQueue` des deux espaces : actions, règle « pas l'auteur », validation à deux, échéances | intégration avec le socle (comptes d'éditeurs de test, demande et approbation par deux comptes, refus de l'auteur) | Comprendre |
| 5. Signature et publication | `PlatformSigner` (adaptateur de développement d'abord), suivi, `PublicationPort`, journal public des corrections (`/corrections`) | faux service de signature (en attente, signé, refusé, mauvais `id`, mauvaise clé) ; mise à jour de même adresse et `created_at` strictement croissant ; page sans JavaScript | Comprendre, puis branchement sur le vrai service avec le Directeur technique |
| 6. Voir | enregistrement de `editorial.voir`, schémas de Voir dans le formulaire | les mêmes tests sur un fait et un parti | Voir |

Chaque étape est une branche, une pull request, des tests verts, `ROADMAP.md` et `CHANGELOG.md` à jour, et une explication pour la propriétaire (comment créer un brouillon de test dans le back-office de préproduction, le faire relire par un deuxième compte et le voir publié dans l'application).

## 8. Questions ouvertes

1. **Voir** : accord sur la forme du schéma (section 1), en particulier `TargetType`, `Positional` et les règles croisées ; liste de ses types et de leurs règles pour l'étape 2 bis.
2. **Directeur technique** : point d'extension des pages d'édition (5.4.1), identité du demandeur (5.4.2), contenu de `ApprovalProof` et défi WebAuthn lié à l'événement (section 6), clé éditoriale par espace ou une seule.
3. **Chef d'équipe (pour la propriétaire, plus tard)** : les contenus réels publiés par le circuit rejoignent-ils l'APK (contenus embarqués) ? Proposition : l'APK embarque un export signé du serveur au moment de sa construction, `content/` restant le jeu `[EXEMPLE]`.
