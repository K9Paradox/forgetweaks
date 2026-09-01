# RLExperiments & RLUtility Project State Summary

## 1. Project Overview & Architecture
- **Mod Name**: `rlutility` (Minecraft 1.12.2 / Forge 14.23.5.2860 / Java 8 Adoptium).
- **Project Root**: `C:\Users\K9\Desktop\RLExperiments`
- **Output Jar Destination**: `C:\Users\K9\AppData\Roaming\PrismLauncher\instances\RLCraft\minecraft\mods\rlutility-1.0.0.jar`
- **Build Toolchain**:
  - JDK: `C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot`
  - Gradle Wrapper: `C:\Users\K9\.gradle\wrapper\dists\gradle-4.9-bin\e9cinqnqvph59rr7g70qubb4t\gradle-4.9\bin\gradle.bat`
  - Command: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"; & $gradle build -x test`

---

## 2. Lock Solver Engine (`LocksHelper.java`)
Automated solver targeting the `melonslise.locks` mod (3.0.0).

### Key Mechanics:
1. **Permutation Architecture**:
   - Locks are strict mathematical permutations of $\{0, 1, \dots, N-1\}$ with no repeated pins.
   - Master/Diamond locks feature up to 11 pins ($11! = 39,916,800$ permutations).
2. **Audio Pitch Mechanics & Decompiled Logic**:
   - `PIN_MATCH` ($1.0$ pitch): Marks pin correct, advances server slot index, and removes that pin value from all other slot candidate sets.
   - `PIN_FAIL` (on survival): Plays sound with pitch $1.25$ if $|actual - guess| \le 1$ (adjacent), or pitch $1.0$ if $|actual - guess| > 1$ (distant).
   - **Pick Breaks (`tryBreakPick`)**: If a pick breaks, the server resets container state and sends `CheckPinResultPacket(false, true)`, **bypassing the fail sound completely**.
3. **Optimizations Implemented**:
   - **Line-Speed Replay**: Solved pins replay at 1 pin per tick (50ms) using direct C2S `CheckPinPacket` packets.
   - **Minimax Entropy Partitioning (`selectOptimalProbeCandidate`)**: Balances adjacent vs distant outcomes to divide candidate sets by 50% on every test.
   - **Instant Permutation Deduction**: Pin $N-1$ (slot 11 on an 11-pin lock) is 100% deduced with zero guessing.
   - **Cleaned GUI**: Unified under single toggle `Auto-Lockpick (Audio/Entropy Solver)`.

---

## 3. Exploit Hub Modules (`FeatureConfig.java` & `GuiUtilityMenu.java`)
- **Keybind**: `O` opens the exploit GUI.
- **Features**:
  - `autoCriticals`: Injects fall packets / velocity triggers on attack to guarantee 100% critical hit damage.
  - `noFall`: Client-side ground spoofing to cancel fall damage.
  - `noSlowdown`: Prevents movement slowdown while eating or drawing bows.
  - `fastTriage`: Automated offhand totem/bandage swapping.
  - `stepSpeed`: Step-height modifier and legit sprint speed adjustments.
  - `clientDebuffNeutralizer`: Clears screen shake and instability effects from Lycanites / FirstAid.
  - `clientItemVacuum`: Nearby item pickup via ItemPhysic interception.
  - `creativeFly`: Client-side flight capabilities.
  - `autoLockpick`: High-speed audio/entropy packet lock solver.

---

## 4. Deployment Command Reference
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
$gradle = "C:\Users\K9\.gradle\wrapper\dists\gradle-4.9-bin\e9cinqnqvph59rr7g70qubb4t\gradle-4.9\bin\gradle.bat"
& $gradle build -x test
Copy-Item -Path "C:\Users\K9\Desktop\RLExperiments\build\libs\rlutility-1.0.0.jar" -Destination "C:\Users\K9\AppData\Roaming\PrismLauncher\instances\RLCraft\minecraft\mods\rlutility-1.0.0.jar" -Force
```
