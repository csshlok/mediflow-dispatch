"""Connects to the gateway notification stream and appends every message to a file.

Usage: python ws_listen.py <ws-url> <output-file> <seconds>
Exit code 0 if the handshake succeeded, 3 if it was rejected.
"""
import asyncio
import sys

import websockets


async def main(url: str, out_path: str, seconds: float) -> int:
    try:
        async with websockets.connect(url, open_timeout=10) as ws:
            with open(out_path, "a", encoding="utf-8") as out:
                out.write("CONNECTED\n")
                out.flush()
                try:
                    while True:
                        message = await asyncio.wait_for(ws.recv(), timeout=seconds)
                        out.write(message + "\n")
                        out.flush()
                except (asyncio.TimeoutError, websockets.ConnectionClosed):
                    return 0
    except websockets.InvalidStatus as e:
        with open(out_path, "a", encoding="utf-8") as out:
            out.write(f"REJECTED {e.response.status_code}\n")
        return 3


if __name__ == "__main__":
    sys.exit(asyncio.run(main(sys.argv[1], sys.argv[2], float(sys.argv[3]))))
