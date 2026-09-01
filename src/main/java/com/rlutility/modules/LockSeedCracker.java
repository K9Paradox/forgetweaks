package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Brute-force recovery of the overworld seed from locks you have already cracked.
 *
 * <h3>Why this is tractable</h3>
 * Every combination on the server is {@code f(lockId, length, overworldSeed)}, and the first two are
 * handed to us in the lockable packet. There is exactly one unknown in the entire world. Crack it
 * once and every lock - including ones you have never seen - is known instantly.
 *
 * <p>The search space is smaller than 64 bits in the cases that matter:</p>
 * <ul>
 *   <li>A seed typed as <b>text</b> in the world-creation screen is stored as
 *       {@code String.hashCode()}, which is a 32-bit int. That is only ~4.3e9 candidates.</li>
 *   <li>A seed typed as a <b>number</b> is used verbatim, and players overwhelmingly type small
 *       ones, so the region around zero is checked first.</li>
 *   <li>Only a fully random seed ({@code new Random().nextLong()}) is out of reach, and this will
 *       report that honestly rather than spin forever.</li>
 * </ul>
 *
 * <h3>Why one lock is usually enough</h3>
 * A length-n lock pins down {@code log2(n!)} bits: an 11-pin lock is ~25 bits, a 5-pin lock ~7. Over
 * a 32-bit space an 11-pin lock leaves only a couple of hundred false positives, and every
 * additional cracked lock filters those out immediately. Candidates are therefore tested against
 * <em>all</em> known locks before being accepted.
 *
 * <p>Verification is cheap - construct a {@link Random}, run at most n-1 {@code nextInt} calls, and
 * bail on the first mismatched pin - so this runs at tens of millions of candidates per second per
 * core, on a background thread pool that never touches the game thread.</p>
 */
public final class LockSeedCracker {

    private LockSeedCracker() {}

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean cancel = new AtomicBoolean(false);
    private static final AtomicLong checked = new AtomicLong(0);
    private static volatile long totalSpace = 0;
    private static volatile String status = "idle";
    /** Total selectivity of the test vectors, in bits. Below ~34 a hit may be a false positive. */
    private static volatile double vectorBits = 0;
    private static List<Thread> workers = new ArrayList<>();

    public static boolean isRunning() {
        return running.get();
    }

    public static String getStatus() {
        return status;
    }

    public static long getChecked() {
        return checked.get();
    }

    public static long getTotal() {
        return totalSpace;
    }

    public static void stop() {
        cancel.set(true);
        status = "cancelled";
    }

    /** A cracked lock used as a test vector: its id plus its proven combination. */
    public static final class Vector {
        final int id;
        final byte[] combination;

        public Vector(int id, byte[] combination) {
            this.id = id;
            this.combination = combination;
        }
    }

    /**
     * Starts the search. Returns false if it could not start (already running, or nothing to
     * verify against).
     */
    public static boolean start() {
        if (running.get()) {
            chat("\u00a7eAlready cracking - use /rlu lockcrack status.");
            return false;
        }

        List<Vector> vectors = LockSeedSolver.vectors();
        if (vectors.isEmpty()) {
            chat("\u00a7cNo cracked locks on file yet. Pick one lock first (the solver does it for");
            chat("\u00a7cfree), then run this - it needs at least one known combination to test against.");
            return false;
        }

        // Longest combination first: it is the most selective filter, so most candidates die on it.
        vectors.sort((a, b) -> Integer.compare(b.combination.length, a.combination.length));

        // Selectivity is log2(n!) summed over every vector; the 32-bit space needs ~32 bits to
        // pin a unique answer, so anything less can produce a false positive.
        double bits = 0;
        for (Vector vector : vectors) {
            for (int i = 2; i <= vector.combination.length; i++) bits += Math.log(i) / Math.log(2);
        }
        vectorBits = bits;

        int best = vectors.get(0).combination.length;
        chat("\u00a76Cracking the world seed from " + vectors.size() + " known lock(s), best is "
                + best + " pins (~" + String.format("%.1f", bits) + " bits of selectivity).");
        if (bits < 34) {
            chat("\u00a7eNote: under ~34 bits the 32-bit space can throw up false positives.");
            chat("\u00a7eIf the recovered seed does not open the next lock, crack one more lock by");
            chat("\u00a7ehand and run this again - it will then be verified against both.");
        }

        cancel.set(false);
        checked.set(0);
        running.set(true);
        status = "searching";

        // Signed 32-bit range covers String.hashCode seeds and any int-typed seed.
        final long lo = Integer.MIN_VALUE;
        final long hi = Integer.MAX_VALUE;
        totalSpace = hi - lo + 1;

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        workers = new ArrayList<>();

        // Pass 1 runs on one thread over the small-number region, because typed numeric seeds are
        // almost always small and this usually finishes before the wide search gets going.
        Thread quick = new Thread(() -> searchRange(-2_000_000L, 2_000_000L, vectors, "near-zero"),
                "RLUtility-SeedCrack-quick");
        quick.setDaemon(true);
        quick.setPriority(Thread.MIN_PRIORITY);
        workers.add(quick);

        long chunk = (hi - lo + 1) / threads;
        for (int t = 0; t < threads; t++) {
            final long start = lo + t * chunk;
            final long end = (t == threads - 1) ? hi : start + chunk - 1;
            Thread worker = new Thread(() -> searchRange(start, end, vectors, "wide"),
                    "RLUtility-SeedCrack-" + t);
            worker.setDaemon(true);
            // Never starve the game thread.
            worker.setPriority(Thread.MIN_PRIORITY);
            workers.add(worker);
        }

        for (Thread worker : workers) worker.start();

        Thread monitor = new Thread(() -> {
            while (running.get() && !cancel.get()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
                boolean alive = false;
                for (Thread worker : workers) if (worker.isAlive()) alive = true;
                if (!alive) break;
            }
            if (running.getAndSet(false) && !cancel.get()) {
                status = "exhausted";
                chat("\u00a7cSearched the whole 32-bit space with no match. The server most likely");
                chat("\u00a7cuses a fully random 64-bit seed, which is not brute-forceable.");
                chat("\u00a77Try asking the server for /seed - many allow it - then /rlu lockseed <seed>.");
            }
        }, "RLUtility-SeedCrack-monitor");
        monitor.setDaemon(true);
        monitor.start();

        return true;
    }

    private static void searchRange(long from, long to, List<Vector> vectors, String label) {
        long localChecked = 0;
        for (long seed = from; seed <= to; seed++) {
            if (cancel.get() || !running.get()) return;

            if (matchesAll(seed, vectors)) {
                found(seed, label);
                return;
            }

            if ((++localChecked & 0xFFFFF) == 0) {
                checked.addAndGet(0x100000);
                localChecked = 0;
            }
        }
        checked.addAndGet(localChecked);
    }

    /** Every known lock must reproduce exactly, otherwise the candidate is wrong. */
    private static boolean matchesAll(long seed, List<Vector> vectors) {
        for (Vector vector : vectors) {
            if (!matches(seed, vector)) return false;
        }
        return true;
    }

    /** Inlined copy of the generator with an early exit on the first wrong pin. */
    private static boolean matches(long seed, Vector vector) {
        int length = vector.combination.length;
        Random rng = new Random(vector.id ^ (Math.abs(seed) * 17317L + seed));
        byte[] c = new byte[length];
        for (byte a = 0; a < length; ++a) c[a] = a;
        for (int a = length - 1; a > 0; --a) {
            int index = rng.nextInt(a + 1);
            byte temp = c[index];
            c[index] = c[a];
            c[a] = temp;
        }
        for (int i = 0; i < length; i++) {
            if (c[i] != vector.combination[i]) return false;
        }
        return true;
    }

    private static synchronized void found(long seed, String label) {
        if (!running.getAndSet(false)) return;
        cancel.set(true);
        status = "found " + seed;

        FeatureConfig.locksWorldSeed = seed;
        FeatureConfig.locksSeedKnown = true;
        FeatureConfig.saveConfig();

        chat("\u00a7a\u00a7lWORLD SEED RECOVERED: \u00a7f" + seed + " \u00a77(" + label + " pass)");
        chat("\u00a7aEvery lock on this server is now solved instantly - no picking needed.");
        if (vectorBits < 34) {
            chat("\u00a7eOnly ~" + String.format("%.1f", vectorBits) + " bits of evidence, so treat this as");
            chat("\u00a7eprovisional. The next lock you open confirms or disproves it automatically.");
        }
    }

    private static void chat(String message) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                EntityPlayerSP player = Minecraft.getMinecraft().player;
                if (player != null) {
                    player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
                }
            });
        } catch (Throwable ignored) {}
    }
}
