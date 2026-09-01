package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocksHelper {

    // Persistent known combination prefix (NEVER wiped on break)
    private static final List<Integer> knownCombination = new ArrayList<Integer>();

    // Array of candidates for each pin slot in the lock (0..15)
    @SuppressWarnings("unchecked")
    private static final Set<Integer>[] slotCandidates = new Set[16];

    static {
        for (int i = 0; i < 16; i++) {
            slotCandidates[i] = new HashSet<Integer>();
        }
    }

    private static int pendingTestPin = -1;
    private static int pendingSlot = -1;
    private static boolean waitingServerResponse = false;
    private static int timeoutTicks = 0;
    private static int lastLiftedCount = 0;
    private static int cooldownDelayTicks = 0;

    // Reflection fields for LockPickingGui
    private static Field frozenField = null;
    private static Field currPinField = null;
    private static Field pinsField = null;
    private static Field lengthField = null;
    private static Field lockPickField = null;

    private static SimpleNetworkWrapper locksNetwork = null;
    private static Constructor<?> checkPinCtor = null;

    @SubscribeEvent
    public void onSoundPlayed(PlaySoundEvent event) {
        if (!FeatureConfig.autoLockpick) return;
        if (event.getSound() == null) return;
        ResourceLocation loc = event.getSound().getSoundLocation();
        if (loc == null) return;

        String path = loc.getResourcePath();
        String domain = loc.getResourceDomain();
        if (!"locks".equals(domain)) return;

        if ("pin.match".equals(path)) {
            if (pendingTestPin != -1 && !knownCombination.contains(pendingTestPin)) {
                knownCombination.add(pendingTestPin);
                eliminateFromAllOtherSlots(pendingTestPin);
            }
            waitingServerResponse = false;
            pendingTestPin = -1;
            pendingSlot = -1;
            timeoutTicks = 0;
            cooldownDelayTicks = 0;
        } else if ("pin.fail".equals(path)) {
            float pitch = event.getSound().getPitch();
            boolean isAdjacent = (pitch > 1.1f);
            int failedPin = pendingTestPin;
            int targetSlot = pendingSlot;

            waitingServerResponse = false;
            pendingTestPin = -1;
            pendingSlot = -1;
            timeoutTicks = 0;
            cooldownDelayTicks = 0;

            if (failedPin != -1 && targetSlot >= 0 && targetSlot < slotCandidates.length) {
                Set<Integer> candidates = slotCandidates[targetSlot];
                candidates.remove(failedPin);

                if (isAdjacent) {
                    Set<Integer> adjacentSet = new HashSet<Integer>();
                    adjacentSet.add(failedPin - 1);
                    adjacentSet.add(failedPin + 1);
                    candidates.retainAll(adjacentSet);
                } else {
                    candidates.remove(failedPin - 1);
                    candidates.remove(failedPin + 1);
                }
            }
        }
    }

    private static void eliminateFromAllOtherSlots(int matchedPin) {
        for (int s = 0; s < slotCandidates.length; s++) {
            if (s != knownCombination.size() - 1) {
                if (!slotCandidates[s].isEmpty()) {
                    slotCandidates[s].remove(matchedPin);
                }
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!FeatureConfig.autoLockpick || event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen currentScreen = mc.currentScreen;

        if (currentScreen == null) {
            cleanupAndReset();
            return;
        }

        String className = currentScreen.getClass().getName();
        if (!className.equals("melonslise.locks.client.gui.LockPickingGui")) {
            cleanupAndReset();
            return;
        }

        try {
            setScreenFrozen(currentScreen, false);

            boolean[] pins = getPinsArray(currentScreen);
            int totalPins = getIntField(currentScreen, "length", (pins != null ? pins.length : 7));
            int liftedCount = countLiftedPins(pins);

            // Singleplayer instant solve
            if (mc.isSingleplayer()) {
                byte[] exactCombination = tryExtractCombination(mc.player.openContainer);
                if (exactCombination != null && exactCombination.length > 0) {
                    if (liftedCount >= exactCombination.length) return;
                    int nextPin = exactCombination[liftedCount] & 0xFF;
                    sendPinDirect(currentScreen, nextPin, totalPins);
                    return;
                }
            }

            // Only terminate solver when ALL pins are lifted
            if (liftedCount >= totalPins) {
                return;
            }

            // Initialize candidate sets with correct dynamic totalPins count if empty
            for (int s = 0; s < totalPins; s++) {
                if (slotCandidates[s].isEmpty()) {
                    for (int p = 0; p < totalPins; p++) {
                        if (!knownCombination.contains(p)) {
                            slotCandidates[s].add(p);
                        }
                    }
                }
            }

            // Detect pick break (liftedCount dropped back down)
            if (liftedCount < lastLiftedCount) {
                if (pendingTestPin != -1 && pendingSlot >= 0 && pendingSlot < slotCandidates.length) {
                    slotCandidates[pendingSlot].remove(pendingTestPin);
                }
                waitingServerResponse = false;
                pendingTestPin = -1;
                pendingSlot = -1;
                timeoutTicks = 0;
                cooldownDelayTicks = 1;
            }
            lastLiftedCount = liftedCount;

            if (cooldownDelayTicks > 0) {
                cooldownDelayTicks--;
                return;
            }

            // 1. FAST REPLAY: Replay known combination path at line speed (1 pin per tick)
            if (liftedCount < knownCombination.size()) {
                int pinToReplay = knownCombination.get(liftedCount);
                sendPinDirect(currentScreen, pinToReplay, totalPins);
                return;
            }

            int currentSlot = knownCombination.size();

            // 2. Handle waiting for server response
            if (waitingServerResponse) {
                timeoutTicks++;
                if (liftedCount > knownCombination.size() && pendingTestPin != -1) {
                    if (!knownCombination.contains(pendingTestPin)) {
                        knownCombination.add(pendingTestPin);
                        eliminateFromAllOtherSlots(pendingTestPin);
                    }
                    waitingServerResponse = false;
                    pendingTestPin = -1;
                    pendingSlot = -1;
                    timeoutTicks = 0;
                } else if (timeoutTicks > 4) {
                    if (pendingTestPin != -1 && pendingSlot >= 0 && pendingSlot < slotCandidates.length) {
                        slotCandidates[pendingSlot].remove(pendingTestPin);
                    }
                    waitingServerResponse = false;
                    pendingTestPin = -1;
                    pendingSlot = -1;
                    timeoutTicks = 0;
                } else {
                    return;
                }
            }

            if (currentSlot >= totalPins) return;

            // 3. Mathematical Permutation Deduction:
            // For the last slot, exactly ONE pin remains in the entire 0..totalPins-1 set!
            if (currentSlot == totalPins - 1) {
                for (int i = 0; i < totalPins; i++) {
                    if (!knownCombination.contains(i)) {
                        sendPinDirect(currentScreen, i, totalPins);
                        waitingServerResponse = true;
                        pendingTestPin = i;
                        pendingSlot = currentSlot;
                        timeoutTicks = 0;
                        return;
                    }
                }
            }

            // 4. Information-Theoretic Entropy Probing
            Set<Integer> candidates = slotCandidates[currentSlot];
            int pinToTest = -1;

            if (candidates.size() == 1) {
                pinToTest = candidates.iterator().next();
            } else {
                pinToTest = selectOptimalProbeCandidate(candidates, totalPins);
            }

            if (pinToTest != -1 && pinToTest < totalPins) {
                sendPinDirect(currentScreen, pinToTest, totalPins);
                waitingServerResponse = true;
                pendingTestPin = pinToTest;
                pendingSlot = currentSlot;
                timeoutTicks = 0;
            }

        } catch (Throwable ignored) {}
    }

    /**
     * Optimal Information-Theoretic Probe Selection:
     * Minimizes max(remainingIfAdjacent, remainingIfDistant).
     */
    private static int selectOptimalProbeCandidate(Set<Integer> candidates, int totalPins) {
        if (candidates.isEmpty()) return -1;
        if (candidates.size() <= 2) return candidates.iterator().next();

        int bestPin = -1;
        int minMaxRemaining = Integer.MAX_VALUE;

        for (int p : candidates) {
            int countAdjacent = 0;
            for (int other : candidates) {
                if (other != p && Math.abs(p - other) == 1) {
                    countAdjacent++;
                }
            }
            int worstCaseRemaining = Math.max(countAdjacent, candidates.size() - 1 - countAdjacent);

            if (worstCaseRemaining < minMaxRemaining) {
                minMaxRemaining = worstCaseRemaining;
                bestPin = p;
            }
        }

        if (bestPin != -1) return bestPin;
        return candidates.iterator().next();
    }

    private static void sendPinDirect(GuiScreen screen, int pin, int totalPins) {
        try {
            if (currPinField == null) {
                currPinField = screen.getClass().getDeclaredField("currPin");
                currPinField.setAccessible(true);
            }
            currPinField.setInt(screen, pin);
            setScreenFrozen(screen, false);

            alignLockPickSprite(screen, pin);

            if (locksNetwork == null) {
                Class<?> networksClass = Class.forName("melonslise.locks.common.init.LocksNetworks");
                Field mainField = networksClass.getDeclaredField("MAIN");
                locksNetwork = (SimpleNetworkWrapper) mainField.get(null);
            }

            if (checkPinCtor == null) {
                Class<?> packetClass = Class.forName("melonslise.locks.common.network.toserver.CheckPinPacket");
                checkPinCtor = packetClass.getConstructor(byte.class);
            }

            if (locksNetwork != null && checkPinCtor != null) {
                IMessage packet = (IMessage) checkPinCtor.newInstance((byte) pin);
                locksNetwork.sendToServer(packet);
            }
        } catch (Throwable ignored) {}
    }

    private static void alignLockPickSprite(GuiScreen screen, int pin) {
        try {
            if (lockPickField == null) {
                lockPickField = screen.getClass().getDeclaredField("lockPick");
                lockPickField.setAccessible(true);
            }
            Object sprite = lockPickField.get(screen);
            if (sprite != null) {
                Field posXField = sprite.getClass().getField("posX");
                posXField.setAccessible(true);
                posXField.setFloat(sprite, (float) (pin * 10 - 11));
            }
        } catch (Throwable ignored) {}
    }

    private static void setScreenFrozen(GuiScreen screen, boolean value) {
        try {
            if (frozenField == null) {
                frozenField = screen.getClass().getDeclaredField("frozen");
                frozenField.setAccessible(true);
            }
            frozenField.setBoolean(screen, value);
        } catch (Throwable ignored) {}
    }

    private static void cleanupAndReset() {
        knownCombination.clear();
        for (int i = 0; i < slotCandidates.length; i++) {
            slotCandidates[i].clear();
        }
        pendingTestPin = -1;
        pendingSlot = -1;
        waitingServerResponse = false;
        timeoutTicks = 0;
        lastLiftedCount = 0;
        cooldownDelayTicks = 0;
    }

    private static byte[] tryExtractCombination(Container container) {
        if (container == null) return null;
        try {
            Field lockableField = container.getClass().getDeclaredField("lockable");
            lockableField.setAccessible(true);
            Object lockable = lockableField.get(container);
            if (lockable == null) return null;

            Field lockField = lockable.getClass().getDeclaredField("lock");
            lockField.setAccessible(true);
            Object lock = lockField.get(lockable);
            if (lock == null) return null;

            Field combinationField = lock.getClass().getDeclaredField("combination");
            combinationField.setAccessible(true);
            Object combination = combinationField.get(lock);
            if (combination instanceof byte[]) {
                byte[] arr = (byte[]) combination;
                if (arr.length > 0) return arr;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean[] getPinsArray(GuiScreen screen) {
        try {
            if (pinsField == null) {
                pinsField = screen.getClass().getDeclaredField("pins");
                pinsField.setAccessible(true);
            }
            Object obj = pinsField.get(screen);
            if (obj instanceof boolean[]) {
                return (boolean[]) obj;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int getIntField(GuiScreen screen, String name, int fallback) {
        try {
            if (lengthField == null && name.equals("length")) {
                lengthField = screen.getClass().getDeclaredField("length");
                lengthField.setAccessible(true);
                return lengthField.getInt(screen);
            }
            Field f = screen.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(screen);
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static int countLiftedPins(boolean[] pins) {
        if (pins == null) return 0;
        int count = 0;
        for (boolean b : pins) {
            if (b) count++;
        }
        return count;
    }
}
