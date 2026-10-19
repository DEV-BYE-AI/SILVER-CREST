# Monsieur Cuisine Connect — Rétro-ingénierie complète

Documentation technique open source du robot **Silvercrest / Lidl Monsieur Cuisine Connect**
(modèle **SPM188A**, SoC **MediaTek MT8321A/MT6580**, Android 6.0).

Cette doc décrit **exactement comment le robot fonctionne**, pour pouvoir écrire une
interface libre équivalente à l'appli d'origine. Tout ici a été reconstruit par
décompilation de l'appli Lidl (`de.silpion.mc2`) + capture série en direct.

---

## 1. Architecture matérielle

```
┌─────────────────────────────┐        UART série         ┌───────────────────────┐
│   Carte CPU (côté écran)    │  /dev/ttyMT0 @ 4800 bauds │  Carte de puissance   │
│  MT8321A + Android 6 + eMMC │ <───────────────────────> │  (MCU + étage moteur) │
│  écran tactile              │        8N1, flags 0       │  moteur + chauffe +   │
│                             │                           │  balance + ventilateur│
└─────────────────────────────┘                           └───────────────────────┘
```

- Deux UART existent : `/dev/ttyMT0` (carte de puissance) et `/dev/ttyMT1`.
- Le **moteur** est un moteur universel **à charbons**, piloté en 230 V par la carte de puissance.
- Un **ventilateur de refroidissement** se déclenche quand la carte passe en état « moteur ON ».

## 2. Pile logicielle de l'appli d'origine

```
MainFragment (UI)
    │  IPC Binder (AIDL)
    ▼
IPCMachineInterface  ──►  HardwareMachineService  (process de.silpion.mc2:mc.machine_service)
                               │
                               ▼
                         PowerBoard (construit/décode les trames)
                               │
                               ▼
                         SerialUtils  (android_serialport_api)
                               │  libserial_port.so (JNI)
                               ▼
                         /dev/ttyMT0 @ 4800
```

- Ouverture du port : `new SerialPort(new File("/dev/ttyMT0"), 4800, 0)` — **4800 bauds, flags = 0**.
  ⚠️ Le constructeur par défaut de `SerialUtils` indique 921600, **mais il n'est jamais utilisé** :
  le service réel ouvre à **4800**. C'est le point qui débloque toute la communication.
- Le service envoie une trame **toutes les 100 ms** en continu (`Lsd$2.run` → `onPerformPeriodicHardwareAction`).

## 3. Trame d'émission (hôte → carte) — 15 octets

```
 idx : 0   1   2   3     4     5      6      7  8  9  10  11    12     13    14
       55  0F  A1  cmd   flag  speed  temp   00 00 00 00  dir   scale  ck    AA
```

| Octet | Rôle |
|------|------|
| 0 | `0x55` début de trame |
| 1 | `0x0F` = longueur (15) |
| 2 | `0xA1` code fixe |
| 3 | **commande** (voir table) |
| 4 | flag : `0xA9` si turbo, sinon `0x00` |
| 5 | **vitesse** 0–10 |
| 6 | **niveau de température** 0–18 |
| 7–10 | `0x00` |
| 11 | **sens** : `0`=anti-horaire, `1`=horaire |
| 12 | échelle balance : `0x00` normal, `0xE9`/`0xE6` calibration |
| 13 | **checksum** = somme(octets 0..12) & 0xFF |
| 14 | `0xAA` fin de trame |

### Octets de commande (octet 3)

| Commande | Valeur |
|---|---|
| MOTOR_ON_HEATER_ON | `0x0F` |
| MOTOR_OFF_HEATER_OFF (tout couper) | `0x05` |
| MOTOR_OFF_HEATER_ON (chauffe seule) | `0x04` |
| MOTOR_ON_HEATER_OFF (turbo) | `0x03` |
| STEAM (vapeur) | `0xC6` |
| TOAST | `0x01` |
| KNEAD (pétrissage) | `0x00` |
| SLEEP | `0x01` |

**Exemple — moteur vitesse 3 :** `55 0F A1 0F 00 03 00 00 00 00 00 00 00 17 AA`

### Niveaux de température (octet 6 → °C)
`0, 37, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 130`

## 4. Trame de réception (carte → hôte) — 27 octets

```
 idx : 0   1   2   3    4     5      ... 9  10 ... 14 15 ... 16 17 18 19 ... 20 21 ... 25   26
       55  1B  B1  état ext   errC       rpmH rpmL  tH tL    poids(32 bits)   fwH fwL     ck   AA
```

| Octet | Rôle |
|------|------|
| 0 | `0x55` début |
| 1 | `0x1B` = longueur (27) |
| 2 | `0xB1` code réponse fixe |
| 3 | **bitfield d'état** (voir ci-dessous) |
| 4 | état étendu (3=STEAM, 4=TOAST, 5=KNEAD) |
| 5 | **code erreur** (voir ci-dessous) |
| 9–10 | **RPM réel** (big-endian : `b9<<8 | b10`) |
| 14–15 | **température réelle** (`b14<<8 | b15`) |
| 16–19 | **poids balance** (32 bits : `b16<<24 | b17<<16 | b18<<8 | b19`) |
| 20–21 | **version firmware** (`b20<<8 | b21`) |
| 25 | checksum = somme(octets 0..24) & 0xFF |
| 26 | `0xAA` fin |

### Bitfield d'état (octet 3)
| Bit | Masque | Signification |
|---|---|---|
| 7 | `0x80` | couvercle **ouvert** |
| 6 | `0x40` | sonde NTC du bol détectée |
| 4 | `0x10` | **erreur** détectée |
| 2 | `0x04` | données balance stables |
| 0–1 | `0x03` | état moteur/chauffe (0=off, 1=on/chauffe, 2=chauffe seule, 3=turbo) |

### Codes d'erreur (octet 5)
`1` = pas d'erreur · `3` = MAX_RUNTIME_EXCEEDED · `4` = MOTOR_OVERHEAT ·
`5` = NO_WATER · `6` = MOTOR · `7` = POWER_INPUT_UNDERVOLTAGE · `8` = POWER_INPUT_OVERVOLTAGE.

### Validation d'une trame reçue
Octet[0]=`0x55`, octet[1]=`27`, octet[2]=`0xB1`, octet[26]=`0xAA`,
checksum octet[25] = somme(0..24) & 0xFF.

## 5. Interface IPC (pour une archi « deux couches »)

L'appli d'origine expose un service **Binder AIDL** : `IPCMachineInterface`
(implémenté par `IPCMachineBinder`). Une appli tierce (même `sharedUserId` système)
peut s'y lier et appeler `onStartRequested(mode, speed, dir, temp)`,
`onStopRequested()`, `onDeviceStateRequested()`, etc., pour piloter le matériel
**via la couche d'origine** plutôt qu'en série direct. Objets échangés :
`DeviceState`, `ScaleState`, `LidState`, `ErrorState`, `WorkMode`, `SpeedLevel`,
`TemperatureLevel`, `Direction`.

`onStartRequested` ne fait **que** régler commande/vitesse/sens/température — aucune
init matérielle cachée. La trame produite est donc strictement celle du §3.

## 6. Accès système requis

- Pour ouvrir `/dev/ttyMT0`, l'appli doit tourner en **uid système**
  (`android:sharedUserId="android.uid.system"`) et être **signée avec la clé plateforme**
  (ici clés AOSP test-keys — la ROM Silvercrest les accepte).
- Avec cet uid, l'appli peut aussi lire `/proc/<pid>/fd` du service Lidl (pour trouver le
  port) et activer/désactiver le paquet Lidl via `PackageManager.setApplicationEnabledSetting`.

## 7. Diagnostic « le moteur ne tourne pas »

Symptômes observés : **RPM = 0**, couvercle vu fermé, **aucune erreur**, le ventilateur
de refroidissement tourne. La carte reçoit la commande (checksum validé), passe en état
« moteur ON » (le ventilo démarre), envoie le courant — mais le rotor ne tourne pas.

➡️ **Diagnostic : circuit moteur ouvert.** Confirmé par le **endurance test de l'appli
Lidl d'origine** : elle commande le moteur à toutes les vitesses et dans les deux sens,
le moteur ne tourne pas non plus. Ce n'est donc **pas** un problème logiciel.

### Réparation (ordre de probabilité)
1. **Charbons (balais)** du moteur — usés/coincés = circuit ouvert (panne n°1).
2. **Connecteur / fils** moteur ↔ carte de puissance.
3. **Fusible thermique** du moteur (continuité).
4. **Continuité du bobinage** (quelques ohms attendus).
5. **Étage de puissance** (triac) sur la carte.

Mesure de référence : multimètre en Ω aux bornes moteur.
`OL/∞` → charbons ou fusible thermique ; `quelques Ω` → étage de puissance de la carte.

---

## 8. Cette appli open source (ce dépôt)

- `serial/PowerBoard.kt` — construit/encode la trame du §3.
- `serial/SerialManager.kt` — ouvre `/dev/ttyMT0 @ 4800`, streame à 100 ms, lit et décode la télémétrie du §4.
- `serial/StockProbe.kt` — détecte le port via les fd du service Lidl, et active/désactive ce service.
- `MainActivity.kt` / `GuideActivity.kt` — l'interface (design clair, recettes, cuisson).
- Architecture « deux couches » : l'appli Lidl reste la couche 1 (matériel), cette appli est la couche 2 (interface).

> Toute la couche logicielle est fonctionnelle et pilote la carte **à l'identique de l'appli Lidl**.
> Le moteur s'activera dès que son circuit électrique sera réparé — côté fer à souder, pas côté code.
