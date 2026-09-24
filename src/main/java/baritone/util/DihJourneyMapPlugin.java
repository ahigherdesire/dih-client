/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.util;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * JourneyMap plugin entrypoint (fabric.mod.json → {@code "journeymap"}).
 *
 * <p>JourneyMap discovers this through the Fabric entrypoint and hands us its {@link IClientAPI}.
 * Fabric only instantiates the entrypoint when JourneyMap asks for it, so this class (and every
 * JourneyMap type) stays unloaded when JourneyMap isn't installed.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class DihJourneyMapPlugin implements IClientPlugin {

    @Override
    public String getModId() {
        return JourneyMapBridge.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        JourneyMapBridge.initialize(api);
        JourneyMapHelper.markReady();
    }
}
