# Four in a Row

An ad-free Connect-Four-style game for Android. Two people on one phone, or one person
against a computer opponent at three strengths.

Called "Four in a Row" rather than "Connect 4" on purpose: *Connect 4* is a Hasbro
trademark. The game itself is not protected, the name is.

## Installing it

It is not on the Play Store, so Android will ask you to confirm a couple of things the
first time. Two ways in.

**With automatic updates.** Install [Obtainium](https://github.com/ImranR98/Obtainium)
(download the APK from its Releases page and open it). Then in Obtainium tap **Add App**,
paste this repo's address, and tap **Add**, then **Install**. Obtainium watches this repo
and installs new versions itself from then on.

```
https://github.com/bbershad/FourInARow
```

**Just the once.** Open [the latest release](../../releases/latest) on your phone,
download `FourInARow-<version>.apk`, and tap it. Simpler, but you will not get updates.

Either way Android asks permission to install from that app the first time, and Play
Protect will say it does not recognise the developer. That warning appears for anything
not installed from the Play Store. Tap **More details**, then **Install anyway**.

Once it is installed, check **Settings > Apps > Four in a Row > Permissions**. It should
list none at all. That is the claim below, verified against Android rather than taken on
trust.

## What it does

- **Two players** on the same phone. **A coin flip decides who goes first, every game** -
  neither colour gets a permanent first-move advantage.
- **Against the computer** at Easy, Medium or Hard. The coin flip applies here too; you
  always play red.
- Undo (against the computer it takes back both its reply and your move, so the turn
  comes back to you), a running score for the session, and a board that highlights the
  winning four.
- Light and dark, following the phone's setting.

## Ad-free, provably

`AndroidManifest.xml` declares **no permissions at all** - in particular no
`android.permission.INTERNET`. Without it the process cannot open a socket, so it cannot
serve an ad, phone home, or report analytics even if something tried to. Anyone can check
that from the app's own permissions list in Android settings.

`tools\rebuild.ps1` re-checks it on every build (`aapt2 dump permissions`) and refuses to
publish if a dependency ever drags `INTERNET` back in through manifest merging.

## The computer opponent

One negamax search with alpha-beta pruning drives all three levels; what separates them is
how deep it looks.

| Level | How it plays |
|---|---|
| Easy | No search. Takes a win it can see this move, blocks a loss about half the time, otherwise random. |
| Medium | Looks 4 plies ahead. Sets up and defends, but does not see a two-move trap coming. |
| Hard | Looks 10 plies ahead with a transposition table. Sees forced wins and losses well before they arrive. |

The board is a bitboard: two `Long`s, seven bits per column with a sentinel bit on top so a
shift cannot wrap one column's ceiling into the next column's floor. That is what makes the
win test four shift-and-mask pairs and lets Hard search 10 plies between taps.

## Layout

```
android\                     Gradle/Kotlin/Compose app
  app\src\main\java\com\bershad\fourinarow\
    game\Board.kt            bitboard, moves, win detection - no Android types
    game\Ai.kt               negamax + the three difficulty levels
    GameViewModel.kt         game state, the coin flip, the computer's turn
    ui\                      Compose theme, board canvas, menu and game screens
  app\src\test\              JUnit tests for the whole engine (no emulator needed)
tools\rebuild.ps1            test -> version bump -> signed APK -> GitHub release
```

## Building and shipping

The toolchain is the same user-space install Benmoji uses, at
`%LOCALAPPDATA%\AndroidBuild\` (JDK 17, Gradle, SDK 35). No admin needed.

```powershell
powershell -ExecutionPolicy Bypass -File tools\rebuild.ps1            # test, build, publish
powershell -ExecutionPolicy Bypass -File tools\rebuild.ps1 -TestOnly  # just the tests
powershell -ExecutionPolicy Bypass -File tools\rebuild.ps1 -NoPublish # build, do not release
```

Delivery is the same route as Benmoji: the phone runs **Obtainium**, which watches this
repo's GitHub releases and installs updates itself.

**Two things must stay true or updates silently stop**, and `rebuild.ps1` handles both:

1. `versionCode` goes up every release, or Android does not recognise the build as an
   update and Obtainium shows nothing.
2. Every APK is signed with the **same** key. The key is in OneDrive, not on this VM and
   not in git - lose it and no future build can install over the copy on the phone.

## Testing it here

This VM **can** run the Android emulator - `emulator -accel-check` reports WHPX usable.
(An older note claimed it could not, "no nested virtualization". That was assumed, never
tested, and it is wrong.)

```powershell
powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1             # release APK
powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1 -DebugApk   # debug build
```

That script boots the `fourinarow` AVD, installs, and launches the app. Three things it
handles that cost real time the first time round:

- It starts the emulator with `Start-Process`, **not** as a child of the calling shell.
  Launched as a background job it gets reaped when the job ends and the window vanishes
  mid-session, logging a clean shutdown with nothing to explain it.
- The **first `adb install` after a boot fails** with a `StorageManager.allocateBytes`
  stack trace. It is a boot race, not a disk problem, so the script retries.
- **"System UI isn't responding"** shortly after boot is the emulator's own launcher
  crashing under software rendering. Tap Wait. It is not this app.

Rendering is software (`-gpu swiftshader_indirect`) because the VM has no GPU and an RDP
session has no usable GL, so everything is slow and `screencap` can lag several frames
behind. A screenshot catching the drop animation mid-flight is not a bug - re-shoot before
concluding anything.

To actually inspect an animation, slow it down. Compose honours the system animator scale:

```powershell
adb shell settings put global animator_duration_scale 10   # 260ms drop becomes 2.6s
adb shell settings put global animator_duration_scale 1    # put it back
```
