# ForgeTweaks (RLUtility)

Custom Minecraft 1.12.2 Forge utility mod and exploit hub tailored for RLCraft and Forge environments.

## Features & Modules

- **Auto-Lockpick (`LocksHelper.java`)**:
  - Automated high-speed audio pitch & entropy solver for `melonslise.locks` (3.0.0).
  - Handles up to 11-pin master/diamond locks ($11!$ permutations) using sound pitch feedback and minimax entropy candidate partitioning.
  - Deduced replay at line-speed (50ms per pin).

- **Combat & Movement**:
  - `autoCriticals`: Injects fall packets and velocity triggers to guarantee 100% critical hit damage.
  - `noFall`: Client-side ground spoofing to negate fall damage.
  - `noSlowdown`: Cancels slowdown while eating or drawing bows.
  - `stepSpeed`: Configurable step height and sprint speed modifier.
  - `fastTriage`: Automated offhand totem/bandage swapping and quick triage.
  - `triggerbot`: Automated attack trigger when crosshair meets valid targets.

- **Utilities & Exploits**:
  - `clientDebuffNeutralizer`: Clears screen shake and instability debuffs from Lycanites / FirstAid.
  - `clientItemVacuum`: Intercepts ItemPhysic drops for automated nearby item pickup.
  - `creativeFly`: Client-side flight capabilities.
  - `fastMine`: Instant block break acceleration.
  - `espRender`: Target highlighting and entity rendering.
  - Commands & Hooks for Quest, Skill, Race, and Reskillable exploits.

## GUI

- Press **`O`** in-game to open the in-game utility configuration menu.

## Build & Installation

### Requirements
- Java 8 JDK (e.g. Eclipse Adoptium OpenJDK 8)
- Gradle 4.9 / ForgeGradle 2.3

### Build Command
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
gradle build -x test
```
The compiled jar will be located at `build/libs/rlutility-1.0.0.jar`.
