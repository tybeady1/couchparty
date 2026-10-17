# Simulator connections

Fairway has native TCP adapters for Open Connect v1 and OpenGolfSim, plus a Fairway JSON feed that preserves unknown metrics for custom software. **Protocol support is implemented and locally tested; gameplay in third-party simulators has not been verified.** No external simulator or license is bundled. These integrations send new live-camera readings, not manual/demo shots or imported clip history.

## Compatibility

| Destination | App preset | Default TCP port | Evidence / status |
|---|---|---:|---|
| [OpenShotGolf](https://github.com/jhauck2/OpenShotGolf) | OpenShotGolf | 49152 | GPL-2.0 repository; its [TCP receiver](https://github.com/jhauck2/OpenShotGolf/blob/main/addons/launch_monitors/common/tcp_server/TcpServer.cs) accepts Open Connect ball data and returns codes 200/501. Source reviewed; full game not run. |
| [HeckleGolfSim](https://github.com/mmancl/HeckleGolfSim) | HeckleGolfSim | 49152 | GPL-2.0 fork with documented Open Connect listener. Source reviewed; full game not run. |
| [GSPro](https://gsprogolf.com/GSProConnectV1.html) | GSPro / Open Connect | 921 | Proprietary paid simulator with documented third-party protocol. Requires the appropriate user-owned license/configuration. Protocol adapter tested locally, not with GSPro. |
| [OpenGolfSim](https://help.opengolfsim.com/desktop/apis/) | OpenGolfSim | 3111 | Public TCP developer API. Free software does not necessarily mean open source: [FUSE](https://github.com/OpenGolfSim/fuse) is source-available under PolyForm Noncommercial, not an OSI open-source license. Protocol adapter tested locally, desktop game not run. |
| Other Open Connect v1 receivers | GSPro / Open Connect + custom port | Configurable | Same wire format; verify each receiver's documentation. No blanket compatibility claim for arbitrary programs or vendor-specific authentication. |
| Your own sim / analysis app | Custom receiver (Fairway JSON) | 2483 | NDJSON schema below; no synthetic measurements. Diagnostic Python receiver included. |

Research checked 2026-10-17. Open source launch-monitor bridges such as PiTrac, Garmin-to-GSPro and MLM2PRO connectors are generally **senders**, not simulators accepting incoming shots; selecting their name would not add compatibility. TGC, E6, Awesome Golf, proprietary device authentication, account services, and remote control are not implemented. No claim of universal support.

## Connect from your phone

1. Start the simulator on your computer, open a range/course, and enable its TCP/Open Connect input. Some receivers only listen while a game scene is open. Select the putter in the simulator for putting.
2. Put phone and computer on the same trusted network. Use the computer's LAN IP; `127.0.0.1` on the phone means the phone itself. Android's desktop emulator uses `10.0.2.2` to reach its host.
3. In Fairway open **Simulator connections**, choose the preset, press **Use selected simulator's default port**, and enter the computer IP. Allow the chosen incoming port through the computer's private-network firewall if necessary. Do not expose these unauthenticated, unencrypted listeners to the internet.
4. For native simulators, read and enable **Allow game assumptions**. Enter your desired assumed full-swing spin and horizontal direction. Select **Enable sending NEW live shots** and save. The custom JSON feed requires no assumptions.
5. **Save + test connection** only opens a socket; it sends no shot and does not prove gameplay compatibility. Return to the live monitor, calibrate, tap the ball, wait for READY, and hit. New detections are transmitted asynchronously.
6. Connection status distinguishes acknowledged shots, rejection, and uncertain delivery. Use **Inspect last outgoing shot** to compare the actual wire payload against the original measurement. **Disable sending immediately** closes the connection and clears queued work.

The app sends to one configured destination at a time. It keeps a small bounded in-memory queue, discards shots delayed over five seconds, and does not automatically retry a shot with uncertain delivery. This prevents an ambiguous timeout from creating duplicate swings. A later new shot can establish a new connection. After changing address/port, save again. Server player notifications are tolerated but do not automatically switch the camera mode. No ready/ball-present heartbeat is fabricated.

## Measurement integrity

Camera estimates contain speed in the image plane and one angle. They do not measure spin, 3D velocity, or carry. Protocols such as Open Connect require fields that are unavailable from this camera setup.

- Default: native simulator sending is blocked until the user explicitly enables game assumptions.
- Full swings: image-plane speed and visible launch angle are used as game inputs. Horizontal direction, total spin and spin axis come from the settings, **not sensors**.
- Putts: the overhead image angle provides game direction (with optional sign reversal). Vertical launch, spin and spin axis are assumed zero. This simple game input does not measure true rolling spin and may affect a simulator's rollout physics.
- Values outside native adapter launch/direction limits are rejected, not silently clamped. Native adapters accept launch 0–45° and horizontal direction −45–45°.
- No carry, club speed, smash factor, or converted full-ball-equivalent speed is invented. Foam, wiffle and range balls retain their measured speed. Most destination simulators model a standard golf ball and their wire formats lack ball-type and uncertainty fields. Consequently simulator results are gameplay estimates, not measured performance of those balls.
- The raw measurement is retained separately in the local last-payload inspector even when a native payload uses assumptions. The Open Connect device ID also identifies game assumptions. OpenGolfSim's documented shot schema has no equivalent metadata field, so the distinction is shown in Fairway rather than added as an undocumented field.

## Raw feed for developers

UTF-8 JSON, one object plus LF per shot, over a persistent TCP client connection. This is a Fairway-defined schema, not a claim that every simulator understands it:

```json
{"schema":"fairway.shot.v1","shot_id":1,"time_ms":1792238400000,"source":"camera_experimental","mode":"putt","ball_type":"NORMAL","image_plane_speed_mps":1.5,"image_angle_deg":-2.0,"angle_kind":"overhead_direction","spin_rpm":null,"carry_m":null,"three_dimensional_launch":null}
```

`shot_id` increases within this installation; clearing app data resets it. Ball types: `NORMAL`, `RANGE`, `FOAM`, `WIFFLE`. Angle is relative to the image horizontal with positive toward image-up, matching the camera reading; it is not a world coordinate. `mode` is `putt` or `swing`; the latter's `angle_kind` is `side_view_launch`. A receiving custom sim must explicitly decide its own game physics/assumptions. No images, names, GPS, or saved sessions are transmitted.

## Local diagnostic receiver and tests

Python 3 standard library only. Run in a terminal:

```powershell
python integrations/receiver.py --bind 127.0.0.1 --port 2483 --protocol raw
# For a physical phone, replace 127.0.0.1 with the computer's LAN IP.
# To inspect native Open Connect instead (with simulator stopped on that port):
python integrations/receiver.py --bind 127.0.0.1 --port 49152 --protocol openconnect
./integrations/test.ps1
```

The receiver prints shots to that terminal, validates basic fields, and acknowledges Open Connect packets. It neither forwards data nor simulates golf. It is a diagnostic aid, not evidence of a third-party game's behavior. The Java suite covers mph/m/s conversion, opt-in gating, unknown-field preservation, putt mapping, invalid angles, fragmented/coalesced TCP response framing, 200 vs 201 vs 501, no-ack timeouts and cancellation. Android integration classes also compile against API 34.
