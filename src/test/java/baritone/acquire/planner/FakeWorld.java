package baritone.acquire.planner;

import baritone.acquire.knowledge.WorldView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A {@link WorldView} with fixed answers; anything not set is unknown (infinitely far, no station). */
final class FakeWorld implements WorldView {
    private final Map<String, Double> blocks = new HashMap<>();
    private final Map<String, Double> entities = new HashMap<>();
    private final Set<String> stations = new HashSet<>();

    FakeWorld block(String block, double distance) {
        blocks.put(block, distance);
        return this;
    }

    FakeWorld entity(String entity, double distance) {
        entities.put(entity, distance);
        return this;
    }

    FakeWorld station(String station) {
        stations.add(station);
        return this;
    }

    @Override
    public double distanceToBlock(String block) {
        return blocks.getOrDefault(block, Double.POSITIVE_INFINITY);
    }

    @Override
    public double distanceToEntity(String entity) {
        return entities.getOrDefault(entity, Double.POSITIVE_INFINITY);
    }

    @Override
    public boolean stationNearby(String station) {
        return stations.contains(station);
    }
}
