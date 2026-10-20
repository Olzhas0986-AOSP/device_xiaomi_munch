package org.lineageos.settings.dirac;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class DiracTileService extends TileService {
    private DiracUtils controller() { return DiracUtils.getInstance(getApplicationContext()); }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = controller().isDiracEnabled();
        tile.setState(!controller().isAvailable() ? Tile.STATE_UNAVAILABLE
                : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        controller().setEnabled(!controller().isDiracEnabled());
        updateTile();
    }
}
