package org.politisationpopulairefrancaise.core.archive

/**
 * A `.ots` file (python-opentimestamps, `DetachedTimestampFile`): the 31-byte header, the major
 * version 1, the hash operation of the timestamped file, its digest, then the proof. Readable by
 * the official client (`ots verify`), and the other way round (D-111).
 */
class OtsFile(val fileHash: OtsOperation, val timestamp: OtsTimestamp) {
    init {
        require(fileHash is OtsOperation.Hashing && fileHash !is OtsOperation.Keccak256) { "SHA-256, RIPEMD-160 or SHA-1 only" }
        require(timestamp.message?.size == (fileHash as OtsOperation.Hashing).digestLength) { "digest of the wrong length" }
    }

    /** The digest of the timestamped file (or text). */
    val digest: ByteArray get() = timestamp.message!!.copyOf()

    fun toByteArray(): ByteArray = OtsWriter().apply {
        writeBytes(HEADER_MAGIC)
        writeVarUInt(MAJOR_VERSION)
        writeByte(fileHash.tag)
        writeBytes(timestamp.message!!)
        timestamp.write(this)
    }.toByteArray()

    companion object {
        /** `\x00OpenTimestamps\x00\x00Proof\x00` followed by eight magic bytes. */
        val HEADER_MAGIC: ByteArray = "004f70656e54696d657374616d7073000050726f6f6600bf89e2e884e89294".hexToByteArray()
        const val MAJOR_VERSION: Long = 1

        fun read(bytes: ByteArray): OtsFile {
            val reader = OtsReader(bytes)
            if (!reader.readBytes(HEADER_MAGIC.size).contentEquals(HEADER_MAGIC)) throw OtsFormatError("not an OpenTimestamps proof")
            if (reader.readVarUInt() != MAJOR_VERSION) throw OtsFormatError("unsupported major version")
            val fileHash = OtsOperation.hashingOf(reader.readByte())
            if (fileHash is OtsOperation.Keccak256) throw OtsFormatError("Keccak-256 is not a file hash")
            val digest = reader.readBytes((fileHash as OtsOperation.Hashing).digestLength)
            val timestamp = OtsTimestamp.read(reader, digest, 0)
            reader.requireEnd()
            return OtsFile(fileHash, timestamp)
        }

        /** A new proof for a SHA-256 [digest], still empty: [OtsBatch] adds its branches. */
        fun forSha256(digest: ByteArray): OtsFile {
            require(digest.size == 32) { "a SHA-256 digest is 32 bytes" }
            return OtsFile(OtsOperation.Sha256, OtsTimestamp(digest.copyOf()))
        }
    }
}

/**
 * A batch of digests stamped together (as `ots stamp` does): each digest gets 16 random bytes
 * appended and is hashed, so a calendar never sees it; the results are paired into a Merkle tree,
 * and only the [root] is sent (D-111). Every proof then reaches the root by its own path.
 */
class OtsBatch(digests: List<ByteArray>, nonce: () -> ByteArray) {
    val files: List<OtsFile> = digests.map { OtsFile.forSha256(it) }
    private val rootNode: OtsTimestamp

    init {
        require(digests.isNotEmpty()) { "at least one digest" }
        var level: List<OtsTimestamp> = files.map { file ->
            val salt = nonce()
            require(salt.size == NONCE_SIZE) { "a nonce is $NONCE_SIZE bytes" }
            file.timestamp.addOperation(OtsOperation.Append(salt)).addOperation(OtsOperation.Sha256)
        }
        while (level.size > 1) {
            val next = ArrayList<OtsTimestamp>()
            var i = 0
            while (i + 1 < level.size) {
                next += hashTogether(level[i], level[i + 1])
                i += 2
            }
            if (i < level.size) next += level[i]
            level = next
        }
        rootNode = level.single()
    }

    /** The only bytes sent to the calendars and to the timestamping authority. */
    val root: ByteArray get() = rootNode.message!!.copyOf()

    /** Adds a calendar's answer (a proof of [root]) to every file of the batch. */
    fun addCalendarProof(proof: OtsTimestamp) = rootNode.merge(proof)

    private fun hashTogether(left: OtsTimestamp, right: OtsTimestamp): OtsTimestamp {
        val leftMessage = left.message!!
        val rightMessage = right.message!!
        // Both paths reach the same node (left ‖ right), then its SHA-256, as in the official client.
        val joined = left.addOperation(OtsOperation.Append(rightMessage))
        right.attachShared(OtsOperation.Prepend(leftMessage), joined)
        return joined.addOperation(OtsOperation.Sha256)
    }

    companion object {
        const val NONCE_SIZE: Int = 16
    }
}
