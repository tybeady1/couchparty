#!/usr/bin/env python3
"""Local protocol diagnostic receiver. This is NOT a golf physics simulator."""
import argparse
import json
import math
import socketserver


def validate(message, protocol):
    if not isinstance(message, dict):
        raise ValueError("Expected a JSON object")
    if protocol == "raw":
        if message.get("schema") != "fairway.shot.v1":
            raise ValueError("Expected fairway.shot.v1")
        values = [message["image_plane_speed_mps"], message["image_angle_deg"]]
        if message.get("spin_rpm") is not None or message.get("carry_m") is not None:
            raise ValueError("Camera feed must preserve unmeasured spin and carry as null")
    elif protocol == "openconnect":
        if message.get("APIversion") != "1" or message.get("Units") != "Yards":
            raise ValueError("Expected Open Connect v1 in yards/mph")
        if not message.get("ShotDataOptions", {}).get("ContainsBallData"):
            raise ValueError("Expected ball data")
        ball = message["BallData"]
        values = [ball[k] for k in ("Speed", "HLA", "VLA", "TotalSpin", "SpinAxis")]
    else:
        if message.get("type") != "shot" or message.get("unit") != "metric":
            raise ValueError("Expected OpenGolfSim metric shot")
        ball = message["shot"]
        values = [ball[k] for k in ("ballSpeed", "verticalLaunchAngle", "horizontalLaunchAngle", "spinSpeed", "spinAxis")]
    if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in values):
        raise ValueError("Metrics must be finite numbers")


class Receiver(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(60)
        try:
            while True:
                line = self.rfile.readline(16385)
                if not line:
                    return
                if len(line) > 16384 or not line.endswith(b"\n"):
                    return
                try:
                    message = json.loads(line)
                    validate(message, self.server.protocol)
                    print(json.dumps(message, ensure_ascii=True), flush=True)
                    if self.server.protocol == "openconnect":
                        self.wfile.write(b'{"Code":200,"Message":"Diagnostic receiver only; no simulated shot"}\n')
                        self.wfile.flush()
                except (ValueError, KeyError, TypeError) as exc:
                    print("Rejected:", str(exc), flush=True)
                    if self.server.protocol == "openconnect":
                        self.wfile.write(b'{"Code":501}\n')
                        self.wfile.flush()
        except (TimeoutError, ConnectionError):
            pass


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="127.0.0.1", help="Use your computer's LAN IP to accept phone connections")
    parser.add_argument("--port", type=int, default=2483)
    parser.add_argument("--protocol", choices=("raw", "openconnect", "ogs"), default="raw")
    args = parser.parse_args()
    with Server((args.bind, args.port), Receiver) as server:
        server.protocol = args.protocol
        print(f"Diagnostic {args.protocol} receiver on {args.bind}:{args.port}; Ctrl+C to stop.", flush=True)
        print("Native simulator packets contain user-approved game assumptions, not measured spin/3D flight.", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
