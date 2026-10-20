/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

private const val TAG = "DolbyTileService"

class DolbyTileService : TileService() {

    private val dolbyController by lazy { DolbyController.getInstance(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        dolbyController.setDsOnAndPersist(!dolbyController.dsOn)
        updateTile()
    }

    private fun updateTile() {
        val enabled = dolbyController.dsOn
        qsTile?.apply {
            state = when {
                !dolbyController.isAvailable -> Tile.STATE_UNAVAILABLE
                enabled -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            subtitle = dolbyController.getProfileName() ?: getString(R.string.dolby_unknown)
            this.updateTile()
        }
    }
}
