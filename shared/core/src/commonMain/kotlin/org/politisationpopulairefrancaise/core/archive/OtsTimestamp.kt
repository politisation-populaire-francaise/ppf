package org.politisationpopulairefrancaise.core.archive

/**
 * One node of an OpenTimestamps proof (python-opentimestamps, `opentimestamps/core/timestamp.py`):
 * a [message], the attestations made on it, and the operations that lead to further nodes.
 *
 * Reading then writing a proof gives the same bytes: the order of the file is kept (the official
 * library writes attestations first, then operations, both sorted, so its files come back as they
 * were). The message is null under an operation this platform cannot compute (Keccak-256).
 */
class OtsTimestamp(val message: ByteArray?) {
    private val attestationList = ArrayList<OtsAttestation>()
    private val operationList = ArrayList<Pair<OtsOperation, OtsTimestamp>>()

    val attestations: List<OtsAttestation> get() = attestationList.toList()
    val operations: List<Pair<OtsOperation, OtsTimestamp>> get() = operationList.toList()

    fun addAttestation(attestation: OtsAttestation) {
        if (attestationList.none { sameAttestation(it, attestation) }) attestationList += attestation
    }

    /** The node reached by [operation], created if needed (an operation appears at most once per node). */
    fun addOperation(operation: OtsOperation): OtsTimestamp {
        operationList.firstOrNull { it.first.sameAs(operation) }?.let { return it.second }
        val child = OtsTimestamp(message?.let { result(operation, it) })
        operationList += operation to child
        return child
    }

    /** Makes [operation] lead to an existing node (two paths of a Merkle tree meet there). */
    internal fun attachShared(operation: OtsOperation, child: OtsTimestamp) {
        val expected = message?.let { result(operation, it) }
        require(expected == null || child.message == null || expected.contentEquals(child.message)) { "the paths do not meet" }
        attach(operation, child)
    }

    private fun attach(operation: OtsOperation, child: OtsTimestamp) {
        if (operationList.any { it.first.sameAs(operation) }) throw OtsFormatError("repeated operation")
        operationList += operation to child
    }

    /** Adds everything [other] (a proof of the same message, from a calendar) says, without duplicates. */
    fun merge(other: OtsTimestamp) {
        if (message != null && other.message != null && !message.contentEquals(other.message)) {
            throw IllegalArgumentException("not the same message")
        }
        other.attestationList.forEach { addAttestation(it) }
        other.operationList.forEach { (operation, child) -> addOperation(operation).merge(child) }
    }

    /** Every attestation of the proof, with the message it attests (null under an uncomputable branch). */
    fun allAttestations(): List<Pair<ByteArray?, OtsAttestation>> {
        val found = ArrayList<Pair<ByteArray?, OtsAttestation>>()
        fun walk(node: OtsTimestamp) {
            node.attestationList.forEach { found += node.message to it }
            node.operationList.forEach { walk(it.second) }
        }
        walk(this)
        return found
    }

    /** True when at least one branch ends in a Bitcoin block (the proof is complete). */
    val isComplete: Boolean get() = allAttestations().any { it.second is OtsAttestation.BitcoinBlockHeader && it.first != null }

    fun toByteArray(): ByteArray = OtsWriter().also { write(it) }.toByteArray()

    internal fun write(writer: OtsWriter) {
        if (attestationList.isEmpty() && operationList.isEmpty()) throw OtsFormatError("empty timestamp")
        val count = attestationList.size + operationList.size
        var index = 0
        attestationList.forEach { attestation ->
            if (index++ < count - 1) writer.writeByte(0xff)
            writer.writeByte(0x00)
            writer.writeBytes(attestation.tag)
            writer.writeVarBytes(attestation.payload())
        }
        operationList.forEach { (operation, child) ->
            if (index++ < count - 1) writer.writeByte(0xff)
            writer.writeByte(operation.tag)
            operation.argumentOrNull()?.let { writer.writeVarBytes(it) }
            child.write(writer)
        }
    }

    companion object {
        const val MAX_DEPTH: Int = 256

        /** A proof received from a calendar: a timestamp of [message] with no file header. */
        fun read(bytes: ByteArray, message: ByteArray): OtsTimestamp {
            val reader = OtsReader(bytes)
            return read(reader, message, 0).also { reader.requireEnd() }
        }

        internal fun read(reader: OtsReader, message: ByteArray?, depth: Int): OtsTimestamp {
            if (depth > MAX_DEPTH) throw OtsFormatError("proof too deep")
            val node = OtsTimestamp(message)
            fun tagOrAttestation(tag: Int) {
                if (tag == 0x00) {
                    node.addAttestation(OtsAttestation.read(reader))
                } else {
                    val operation = OtsOperation.read(tag, reader)
                    val child = read(reader, message?.let { result(operation, it) }, depth + 1)
                    node.attach(operation, child)
                }
            }
            var tag = reader.readByte()
            while (tag == 0xff) {
                tagOrAttestation(reader.readByte())
                tag = reader.readByte()
            }
            tagOrAttestation(tag)
            return node
        }

        private fun result(operation: OtsOperation, message: ByteArray): ByteArray? {
            if (message.size > OtsOperation.MAX_MESSAGE_LENGTH) throw OtsFormatError("message too long")
            return operation.apply(message)
        }

        private fun sameAttestation(a: OtsAttestation, b: OtsAttestation): Boolean =
            a.tag.contentEquals(b.tag) && a.payload().contentEquals(b.payload())
    }
}
