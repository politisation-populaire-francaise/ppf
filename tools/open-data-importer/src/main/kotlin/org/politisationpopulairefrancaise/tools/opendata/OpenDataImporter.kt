package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * The importer of parliamentary open data (D-064). One run, then it stops (no loop: the systemd
 * timer starts it again).
 *
 *     java -jar ppf-open-data-importer.jar [--source example|official] [--only an,senat,pe] [--history] --out <dir> --key <nsec file>
 *
 * - `--source` (or `PPF_OPEN_DATA_SOURCE`): `example` (default) reads the fictional files bundled
 *   with the importer; `official` downloads the files of the institutions (production only).
 * - `--only`: the sources to refresh, among `an,senat,pe` (daily) and `an-history,senat-history,pe-history`
 *   (history of the ballots); the others are kept from the previous set. Default: every daily
 *   source, plus the history in `example` mode only.
 * - `--history` (no value): the three history sources (with the daily sources listed by `--only`, if any).
 * - `--out` (or `PPF_OPEN_DATA_DIR`): the folder of the packages, served read-only by the server.
 * - `--key` (or `PPF_IMPORT_KEY_FILE`): the file holding the import key (`nsec1…`), out of the repository.
 *
 * Exit codes: see [ExitCodes] (0 ok, 1 usage, 2 download failed, 3 refused by the guard, 4 unreadable
 * source data, 5 key error). On any failure the previous packages stay as they were.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "keygen") exitProcess(runKeyGen(args.drop(1)))
    exitProcess(runCommandLine(args.toList(), System::getenv))
}

data class Options(
    val mode: SourceMode,
    val only: Set<SourceId>,
    val out: Path,
    val keyFile: Path,
    /** Official files already on disk (`amo10/`, `scrutins/`, `dossiers/`, `senat/`), read instead of downloaded. */
    val from: Path? = null,
    /** The history sources to rebuild. */
    val history: Set<HistorySourceId> = emptySet(),
    /** The presentations of the presidential election (VO-37): `cc` in `--only`, or a dataset given in `PPF_SPONSORSHIPS_DATASET`. */
    val sponsorships: Boolean = false,
    val sponsorshipsDataset: String? = null,
)

const val USAGE: String = "usage: ppf-open-data-importer [--source example|official] [--only an,senat,pe,an-history,senat-history,pe-history] [--history] [--from <dir>] --out <dir> --key <nsec file>\n" +
    "  environment: PPF_OPEN_DATA_SOURCE, PPF_OPEN_DATA_DIR, PPF_IMPORT_KEY_FILE, PPF_SPONSORSHIPS_DATASET"

/** The options, or the reason they are wrong. */
fun parseOptions(args: List<String>, env: (String) -> String?): Result<Options> = runCatching {
    val values = HashMap<String, String>()
    var historyFlag = false
    var i = 0
    while (i < args.size) {
        val name = args[i]
        if (name == "--history") {
            require(!historyFlag) { "--history given twice" }
            historyFlag = true
            i++
            continue
        }
        require(name in setOf("--source", "--only", "--out", "--key", "--from")) { "unknown argument $name" }
        require(i + 1 < args.size) { "$name needs a value" }
        require(values.put(name, args[i + 1]) == null) { "$name given twice" }
        i += 2
    }
    val mode = when (val source = (values["--source"] ?: env("PPF_OPEN_DATA_SOURCE")?.takeIf { it.isNotBlank() } ?: "example").trim().lowercase()) {
        "example" -> SourceMode.EXAMPLE
        "official" -> SourceMode.OFFICIAL
        else -> throw IllegalArgumentException("unknown source $source")
    }
    val listed = values["--only"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.onEach {
        require(SourceId.of(it) != null || HistorySourceId.of(it) != null || it == SPONSORSHIPS_ID) { "unknown source id $it" }
    }
    require(listed == null || listed.isNotEmpty()) { "--only names no source" }
    val only = when {
        listed != null -> listed.mapNotNull(SourceId::of).toSet()
        historyFlag -> emptySet()
        else -> SourceId.entries.toSet()
    }
    val history = when {
        historyFlag -> HistorySourceId.entries.toSet()
        listed != null -> listed.mapNotNull(HistorySourceId::of).toSet()
        // The fictional set always holds the history too; the official history is imported apart.
        mode == SourceMode.EXAMPLE -> HistorySourceId.entries.toSet()
        else -> emptySet()
    }
    val out = (values["--out"] ?: env("PPF_OPEN_DATA_DIR")?.takeIf { it.isNotBlank() }) ?: throw IllegalArgumentException("--out (or PPF_OPEN_DATA_DIR) is required")
    val key = (values["--key"] ?: env("PPF_IMPORT_KEY_FILE")?.takeIf { it.isNotBlank() }) ?: throw IllegalArgumentException("--key (or PPF_IMPORT_KEY_FILE) is required")
    val from = values["--from"]?.let { Path.of(it.trim()) }
    require(from == null || mode == SourceMode.OFFICIAL) { "--from reads official files: use it with --source official" }
    val dataset = env("PPF_SPONSORSHIPS_DATASET")?.trim()?.takeIf { it.isNotEmpty() }
    // Asked by name in `--only`, or by default in production once the dataset of the Conseil constitutionnel is known.
    val sponsorships = listed?.contains(SPONSORSHIPS_ID) ?: (mode == SourceMode.OFFICIAL && dataset != null && !historyFlag)
    Options(mode, only, Path.of(out.trim()), Path.of(key.trim()), from, history, sponsorships, dataset)
}

/** The identifier of the presentations of the presidential election in `--only` (and of their source in the index). */
const val SPONSORSHIPS_ID: String = "cc"

/** Reads the import key; refuses the example key for official data (it would make real data look fictional, and the reverse). */
fun loadKey(file: Path, mode: SourceMode): Result<IdentityKeys> = runCatching {
    require(Files.isRegularFile(file)) { "key file not found" }
    val keys = try {
        IdentityKeys.fromNsec(Files.readString(file).trim())
    } catch (e: Exception) {
        throw IllegalArgumentException("the key file does not hold an nsec key")
    }
    if (mode == SourceMode.OFFICIAL) require(keys.publicKey.hex != OpenDataKeys.EXAMPLE) { "the example key never signs official data" }
    keys
}

fun runCommandLine(args: List<String>, env: (String) -> String?, sources: ((SourceMode) -> ImportSources)? = null): Int {
    val options = parseOptions(args, env).getOrElse {
        System.err.println("error: ${it.message}\n$USAGE")
        return ExitCodes.USAGE
    }
    val keys = loadKey(options.keyFile, options.mode).getOrElse {
        System.err.println("key error: ${it.message}")
        return ExitCodes.KEY_ERROR
    }
    if (options.mode == SourceMode.EXAMPLE && keys.publicKey.hex != OpenDataKeys.EXAMPLE) {
        println("note: this key is not OpenDataKeys.EXAMPLE, the application will not trust these packages")
    }
    return try {
        val chosen = sources?.invoke(options.mode) ?: when (options.mode) {
            SourceMode.EXAMPLE -> ExampleSources()
            SourceMode.OFFICIAL -> options.from?.let(::LocalOfficialSources) ?: OfficialSources(sponsorshipsDataset = options.sponsorshipsDataset)
        }
        Importer(chosen, keys, options.out, options.only, options.history, sponsorships = options.sponsorships).run()
    } catch (e: UnreadableSourceException) {
        System.err.println("unreadable source data: ${e.message}")
        ExitCodes.UNREADABLE_SOURCE
    } catch (e: Exception) {
        // Never the message: it could quote a value of the data.
        System.err.println("import failed: ${e::class.qualifiedName}; the previous packages are kept")
        ExitCodes.UNREADABLE_SOURCE
    } finally {
        keys.destroy()
    }
}
