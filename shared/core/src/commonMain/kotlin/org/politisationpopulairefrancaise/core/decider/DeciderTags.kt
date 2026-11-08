package org.politisationpopulairefrancaise.core.decider

/**
 * The tags of the "Décider" building blocks (docs/FORMATS.md, section 4 septies). Like the blocks
 * of "Voir", a block is a NIP-23 event (kind 30023) whose `ppf-type` says what it is; its Markdown
 * body stays readable by any Nostr client, its structured data lives in these tags. Multi-field
 * tags are positional; an empty field means "unknown". The common tags of "Voir" (`ppf-source`,
 * `ppf-archive`, `ppf-correction`, `ppf-next`, links `a`) keep their meaning here.
 */
object DeciderTags {
    // Official petitions (cahier fonctionnel, M10; DE-07).
    /** Where the petition is signed: `assemblee`, `senat`, `cese`, `rip`, `ice` ([PetitionPlatform]). */
    const val PLATFORM = "ppf-platform"
    /** The page of the petition on the official platform; any other site is refused. */
    const val OFFICIAL_URL = "ppf-official-url"
    /** The number given by the platform ("n° 3014"). */
    const val REFERENCE = "ppf-reference"
    const val OPENED = "ppf-opened"
    /** The last day of the collection, when the platform sets one. */
    const val CLOSES = "ppf-closes"
    /** A reading of the official counter: `count | date | source` (repeat the tag for the history). */
    const val COUNT = "ppf-count"
    /** How many departments the signatories come from, when the platform publishes it: `number | date`. */
    const val DEPARTMENTS = "ppf-departments"
    /** What happened to the petition: `date | label` (referred to a committee, debate decided...). */
    const val MILESTONE = "ppf-milestone"
    /**
     * The signatures required by this procedure, when the law sets them procedure by procedure
     * (shared-initiative referendum: one tenth of the registered voters, published for each one).
     */
    const val REQUIRED = "ppf-required"
    /** Link role from an official petition to the demand of the wall it carries (D-115). */
    const val ROLE_DEMAND = "revendication"

    /** `ouverte` or `close` ([PetitionStatus]). */
    const val PETITION_STATUS = "ppf-petition-status"

    // The democratic pact (cahier fonctionnel, M13.5; DE-08).
    /** The election: `identifier | label | first round (AAAA-MM-JJ, optional)`. */
    const val ELECTION = "ppf-election"
    /** One measure: `identifier | title | precise commitment | text to change`, in their order. */
    const val MEASURE = "ppf-measure"
    /** The day the same text was sent to every candidate. */
    const val SENT = "ppf-sent"
    /** The same deadline for every candidate. */
    const val DEADLINE = "ppf-deadline"
    /** `proposee` (starting list, to be voted), `envoye`, `clos` ([PacteStatus]). */
    const val PACTE_STATUS = "ppf-pacte-status"
    /** Link role of every candidate of the election, from the pact to their sheet in "Voir". */
    const val ROLE_CANDIDATE = "candidat"

    // The answer of a candidate (an "engagement").
    /** Link role from an answer (or a fact of "Voir" that follows a measure) to its pact. */
    const val ROLE_PACTE = "pacte"
    /** The fingerprint of the text the candidate answered ([Pacte.fingerprint]). */
    const val PACTE_TEXT = "ppf-pacte-text"
    /** The answer on one measure: `measure identifier | signe or refuse | comment (optional)`. */
    const val ANSWER = "ppf-answer"
    /** The day the answer was received. */
    const val RECEIVED = "ppf-received"

    /** On a fact of "Voir" linked to a pact: the measure it follows after the election. */
    const val PACTE_MEASURE = "ppf-pacte-measure"

    // The comparator of an election (cahier fonctionnel, M13.7; DE-16).
    /** One theme of a comparator, in its order (repeat the tag), or the theme of a position. */
    const val THEME = "ppf-theme"
    /** A short quotation of the candidate: `text | page (https) | date (AAAA-MM-JJ)`. */
    const val QUOTE = "ppf-quote"

    /**
     * One round of the election of a comparator: `number | voting day | first voting day (overseas, optional) |
     * closing of the last polling stations (HH:MM, Paris time, optional: 20:00)`. Sets the freeze ([ElectionRound]).
     */
    const val ROUND = "ppf-round"

    /** The request of a declared candidate: `candidate (identifier of the sheet) | requested on | added on` ([CandidateRequest]). */
    const val REQUEST = "ppf-request"

    /** The same delay for everyone between a request and the addition, in days. */
    const val ADDITION_DELAY = "ppf-addition-delay"

    /** The official list of the candidates: `page (https) | date of publication` ([OfficialList]). */
    const val OFFICIAL_LIST = "ppf-official-list"

    /** The published words of one theme, for the past votes (D-117): `theme | word | word…` ([ThemeWords]). */
    const val THEME_WORDS = "ppf-theme-words"

    /** One version of the words, the public history of the rule: `version | date | reason` ([WordsVersion]). */
    const val WORDS_VERSION = "ppf-words-version"

    /** The party of a candidate in the history packages: `candidate | party | from | to (optional)` ([CandidateParty]). */
    const val PARTY = "ppf-party"
}
