package org.politisationpopulairefrancaise.app

import android.content.Context
import android.net.ConnectivityManager
import org.politisationpopulairefrancaise.core.voir.parlement.NetworkConditions

/**
 * The network as the background work sees it (D-102): the data saver mode of Android (« Économiseur de
 * données », which restricts the background) and whether the current network is unmetered (Wi-Fi,
 * Ethernet). Read only, nothing is sent; when unknown, the most careful answer.
 */
object AndroidNetwork {
    fun conditions(context: Context): NetworkConditions {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkConditions(dataSaver = true, unmetered = false)
        val dataSaver = runCatching { manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }.getOrDefault(true)
        val unmetered = runCatching { !manager.isActiveNetworkMetered }.getOrDefault(false)
        return NetworkConditions(dataSaver = dataSaver, unmetered = unmetered)
    }
}
