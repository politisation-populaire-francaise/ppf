package org.politisationpopulairefrancaise.core.local

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.ConnectionManager
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock

/**
 * The in-memory database of the tests, safe when several threads use it at once.
 *
 * SQLDelight's in-memory SQLite driver keeps one connection and one current transaction for the
 * whole process: a query or a transaction started on a second thread (the bundled contents imported
 * on `Dispatchers.Default` while a screen reads or erases) took the transaction of the first thread
 * as its own and changed its sets of pending notifications while the first one went through them.
 * Tests failed at random (`ConcurrentModificationException`, `ArrayIndexOutOfBoundsException` in
 * `postTransactionCleanup`), seen in DeviceSecurityTest, RoomModerationScreenTest and
 * PrivateMessagesTest until 11 November 2026.
 *
 * Here the single connection is lent to one thread at a time (a reentrant lock, held from the
 * start of a transaction to its end), each thread has its own current transaction, and the
 * listeners are kept under a lock. The database of the applications (a file) already gives each
 * thread its own connection and is not concerned.
 */
internal class SharedMemoryDriver : JdbcDriver() {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:")
    private val lock = ReentrantLock()
    private val transactions = ThreadLocal<ConnectionManager.Transaction?>()
    private val listeners = linkedMapOf<String, MutableSet<Query.Listener>>()

    override var transaction: ConnectionManager.Transaction?
        get() = transactions.get()
        set(value) = transactions.set(value)

    /** Taken outside a transaction for one statement, or at the start of a transaction until its end. */
    override fun getConnection(): Connection {
        lock.lock()
        return connection
    }

    override fun closeConnection(connection: Connection) {
        lock.unlock()
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners.getOrPut(it) { linkedSetOf() }.add(listener) } }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners[it]?.remove(listener) } }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val toNotify = synchronized(listeners) { queryKeys.flatMapTo(linkedSetOf()) { listeners[it].orEmpty() } }
        toNotify.forEach(Query.Listener::queryResultsChanged)
    }

    override fun close() {
        connection.close()
    }
}
