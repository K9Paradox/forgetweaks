package com.rlutility.modules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Offline combination solver for melonslise's Locks.
 *
 * <h3>The break</h3>
 * A lock's combination is <b>never transmitted and never persisted</b>. From {@code LocksUtil}:
 *
 * <pre>
 * // "Reads a lock as a consecutive integer, byte and boolean from the given buffer.
 * //  Does not include the lock's combination."
 * public static Lock readLockFromBuffer(ByteBuf buf) {
 *     return new Lock(buf.readInt(), (int) buf.readByte(), buf.readBoolean());
 * }
 *
 * public static NBTTagCompound writeLockToNBT(Lock lock) {
 *     nbt.setInteger(KEY_ID, lock.id);
 *     nbt.setByte(KEY_LENGTH, (byte) lock.combination.length);
 *     nbt.setBoolean(KEY_LOCKED, lock.locked);   // no combination
 * }
 * </pre>
 *
 * It is instead <em>regenerated</em> every time a lock is loaded or received:
 *
 * <pre>
 * public Lock(int id, int length, boolean locked) {
 *     this.rng = new Random(id ^ Math.abs(overworldSeed) * 17317L + overworldSeed);
 *     for (byte a = 0; a &lt; length; ++a) combination[a] = a;
 *     LocksUtil.shuffle(this.combination, this.rng);
 * }
 * </pre>
 *
 * <p>So every combination in the world is a pure function of three values, two of which the server
 * hands us for free in the lockable packet: the lock <b>id</b>, its <b>length</b>, and the
 * <b>overworld seed</b>. One unknown, shared by every lock on the server. Recover it once and every
 * lock - present and future - is solved instantly, with no picking at all.</p>
 *
 * <h3>Getting the seed</h3>
 * <ul>
 *   <li><b>Single player / LAN</b>: the integrated server is right here, so the client's own
 *       {@code Lock} objects already hold the true combination. Nothing to do.</li>
 *   <li><b>Dedicated server</b>: the client has no seed, so its locally generated combinations are
 *       wrong. Supply the seed with {@code /rlu lockseed <seed>} - many servers publish it - and
 *       everything unlocks. If you do not have it, the free brute-force solver still works, and each
 *       lock it cracks is stored as a test vector so a later seed guess can be verified instantly.</li>
 * </ul>
 *
 * <p>Because the mapping is deterministic, a solved combination is also valid forever: the cache is
 * keyed by lock id and survives restarts.</p>
 */
public final class LockSeedSolver {

    private LockSeedSolver() {}

    /** id -> known-good combination, learned by brute force or computed from a verified seed. */
    private static final Map<Integer, byte[]> known = new LinkedHashMap<>();

    private static File cacheFile = null;
    private static boolean loaded = false;

    // ------------------------------------------------------------- algorithm

    /**
     * Exact replication of {@code new Lock(id, length, locked)}.
     *
     * <p>Note the operator precedence in the original: {@code ^} binds looser than {@code +} and
     * {@code *}, so the seed is {@code id ^ ((abs(s) * 17317L) + s)}, not {@code (id ^ abs(s)*17317L) + s}.</p>
     */
    public static byte[] compute(int id, int length, long overworldSeed) {
        if (length <= 0) return new byte[0];
        Random rng = new Random(id ^ (Math.abs(overworldSeed) * 17317L + overworldSeed));
        byte[] combination = new byte[length];
        for (byte a = 0; a < length; ++a) combination[a] = a;
        // LocksUtil.shuffle - top-down Fisher-Yates, must match exactly.
        for (int a = length - 1; a > 0; --a) {
            int index = rng.nextInt(a + 1);
            byte temp = combination[index];
            combination[index] = combination[a];
            combination[a] = temp;
        }
        return combination;
    }

    // ------------------------------------------------------------------ seed

    public static boolean hasSeed() {
        return FeatureConfig.locksSeedKnown;
    }

    public static long getSeed() {
        return FeatureConfig.locksWorldSeed;
    }

    /**
     * Checks a candidate seed against every combination we have already cracked.
     * Returns true (and stores the seed) only if it reproduces all of them.
     */
    public static boolean trySeed(long seed) {
        load();
        if (known.isEmpty()) {
            // Nothing to verify against - accept it provisionally.
            FeatureConfig.locksWorldSeed = seed;
            FeatureConfig.locksSeedKnown = true;
            FeatureConfig.saveConfig();
            return true;
        }
        for (Map.Entry<Integer, byte[]> entry : known.entrySet()) {
            byte[] expected = entry.getValue();
            byte[] actual = compute(entry.getKey(), expected.length, seed);
            if (!java.util.Arrays.equals(expected, actual)) return false;
        }
        FeatureConfig.locksWorldSeed = seed;
        FeatureConfig.locksSeedKnown = true;
        FeatureConfig.saveConfig();
        return true;
    }

    public static void clearSeed() {
        FeatureConfig.locksSeedKnown = false;
        FeatureConfig.saveConfig();
    }

    /** Number of cracked locks available as verification vectors. */
    public static int knownCount() {
        load();
        return known.size();
    }

    // ----------------------------------------------------------------- lookup

    /**
     * Best known combination for a lock, or null if we have neither a cached result nor a seed.
     */
    public static byte[] lookup(int id, int length) {
        load();
        byte[] cached = known.get(id);
        if (cached != null && cached.length == length) return cached;
        if (hasSeed()) return compute(id, length, getSeed());
        return null;
    }

    /** Records a combination proven correct by actually opening the lock. */
    public static void remember(int id, byte[] combination) {
        if (combination == null || combination.length == 0) return;
        load();
        known.put(id, combination.clone());
        save();
    }

    /**
     * After learning a new combination, see whether any already-suspected seed still holds.
     * If the stored seed no longer reproduces every vector it is dropped, so we never act on a
     * seed that has been disproven.
     */
    public static void revalidateSeed() {
        if (!hasSeed()) return;
        long seed = getSeed();
        load();
        for (Map.Entry<Integer, byte[]> entry : known.entrySet()) {
            byte[] expected = entry.getValue();
            if (!java.util.Arrays.equals(expected, compute(entry.getKey(), expected.length, seed))) {
                FeatureConfig.locksSeedKnown = false;
                FeatureConfig.saveConfig();
                return;
            }
        }
    }

    // ------------------------------------------------------------ persistence

    public static void init(File configDir) {
        cacheFile = new File(configDir, "rlutility_locks.cfg");
        loaded = false;
        load();
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (cacheFile == null) cacheFile = new File("config/rlutility_locks.cfg");
            if (!cacheFile.exists()) return;
            Properties props = new Properties();
            try (FileReader reader = new FileReader(cacheFile)) {
                props.load(reader);
            }
            for (String key : props.stringPropertyNames()) {
                try {
                    int id = Integer.parseInt(key);
                    String[] parts = props.getProperty(key).split(",");
                    byte[] combination = new byte[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        combination[i] = Byte.parseByte(parts[i].trim());
                    }
                    known.put(id, combination);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void save() {
        try {
            if (cacheFile == null) cacheFile = new File("config/rlutility_locks.cfg");
            if (cacheFile.getParentFile() != null) cacheFile.getParentFile().mkdirs();
            Properties props = new Properties();
            for (Map.Entry<Integer, byte[]> entry : known.entrySet()) {
                StringBuilder sb = new StringBuilder();
                for (byte b : entry.getValue()) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(b);
                }
                props.setProperty(String.valueOf(entry.getKey()), sb.toString());
            }
            try (FileWriter writer = new FileWriter(cacheFile)) {
                props.store(writer, "RLUtility - cracked Locks combinations, keyed by lock id");
            }
        } catch (Exception ignored) {}
    }

    public static String format(byte[] combination) {
        if (combination == null) return "?";
        StringBuilder sb = new StringBuilder();
        for (byte b : combination) {
            if (sb.length() > 0) sb.append('-');
            sb.append(b);
        }
        return sb.toString();
    }
}
