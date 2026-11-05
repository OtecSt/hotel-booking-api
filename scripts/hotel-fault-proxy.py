"""Loopback-only test proxy that can drop Hotel's successful reservation replies."""

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock
from time import sleep
from urllib.error import HTTPError
from urllib.request import Request, urlopen


class HotelProxy(BaseHTTPRequestHandler):
    drops_remaining = 0
    dropped = 0
    block_releases = False
    blocked_releases = 0
    lock = Lock()

    def do_POST(self):
        if self.path == "/__test/drop-confirmations":
            with self.lock:
                HotelProxy.drops_remaining = 3
                HotelProxy.dropped = 0
                HotelProxy.block_releases = True
                HotelProxy.blocked_releases = 0
            self.send_response(204)
            self.end_headers()
            return
        if self.path == "/__test/allow-releases":
            with self.lock:
                HotelProxy.block_releases = False
            self.send_response(204)
            self.end_headers()
            return
        self.forward()

    def do_GET(self):
        if self.path == "/__test/status":
            with self.lock:
                body = json.dumps({"dropped": self.dropped, "blockedReleases": self.blocked_releases}).encode()
            self.reply(200, body, "application/json")
            return
        self.forward()

    def do_PUT(self):
        self.forward()

    def do_DELETE(self):
        self.forward()

    def forward(self):
        size = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(size) if size else None
        if self.path.endswith("/release"):
            with self.lock:
                blocked = HotelProxy.block_releases
                if blocked:
                    HotelProxy.blocked_releases += 1
            if blocked:
                self.reply(503, b"", None)
                return
        headers = {name: self.headers[name] for name in
                   ("Authorization", "Content-Type", "Accept", "X-Trace-Id") if name in self.headers}
        request = Request(self.server.hotel_url + self.path, data=body, method=self.command, headers=headers)
        try:
            response = urlopen(request, timeout=5)
        except HTTPError as error:
            response = error

        with response:
            content = response.read()
            status = response.status
            content_type = response.headers.get("Content-Type")

        if self.path.endswith("/confirm-availability") and status == 204:
            with self.lock:
                drop = HotelProxy.drops_remaining > 0
                if drop:
                    HotelProxy.drops_remaining -= 1
                    HotelProxy.dropped += 1
            if drop:
                sleep(2.5)
                return

        self.reply(status, content, content_type)

    def reply(self, status, body, content_type):
        self.send_response(status)
        if content_type:
            self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    with ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), HotelProxy) as server:
        server.hotel_url = sys.argv[2]
        server.serve_forever()
