package com.shuttermute

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ShutterMuteTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        ShutterSetting.toggle(applicationContext)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val muteAllowed = ShutterSetting.isMuteAllowed(applicationContext)
        tile.state = if (muteAllowed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = getString(
            if (muteAllowed) R.string.tile_desc_on else R.string.tile_desc_off
        )
        tile.updateTile()
    }
}
