"""Exercise the booking API through a running Gateway and demo catalog."""

import json
import os
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from threading import Barrier
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4


BASE_URL = os.environ.get("GATEWAY_URL", "http://localhost:8080").rstrip("/")


def request(method, path, *, payload=None, token=None, key=None, expected=200):
    headers = {"Accept": "application/json"}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if key:
        headers["Idempotency-Key"] = key

    req = Request(BASE_URL + path, data=body, headers=headers, method=method)
    try:
        response = urlopen(req, timeout=25)
    except HTTPError as error:
        response = error

    with response:
        result = response.read()
        statuses = (expected,) if isinstance(expected, int) else expected
        if response.status not in statuses:
            raise AssertionError(f"{method} {path} returned {response.status}: {result.decode()}")
        return response.status, json.loads(result) if result else None


def register():
    credentials = {"username": f"smoke-{uuid4()}", "password": str(uuid4())}
    request("POST", "/api/auth/register", payload=credentials, expected=201)
    _, response = request("POST", "/api/auth/login", payload=credentials)
    return response["accessToken"]


def main():
    request("GET", "/api/hotels", expected=401)
    owner = register()
    other = register()
    request("GET", "/api/hotels", token=owner)

    start = date.today() + timedelta(days=10)
    end = start + timedelta(days=2)
    dates = f"startDate={start}&endDate={end}"
    _, available = request("GET", f"/api/rooms?{dates}", token=owner)
    assert available, "Start Hotel Service with the demo profile and free rooms"
    room_id = available[0]["id"]
    payload = {"roomId": room_id, "startDate": str(start), "endDate": str(end)}

    request("POST", f"/api/rooms/{room_id}/release", token=owner, expected=404)
    key = str(uuid4())
    _, booking = request("POST", "/api/bookings", payload=payload, token=owner, key=key, expected=201)
    assert booking["status"] == "CONFIRMED"
    _, repeated = request("POST", "/api/bookings", payload=payload, token=owner, key=key, expected=201)
    assert repeated["id"] == booking["id"]

    booking_path = f"/api/bookings/{booking['id']}"
    request("GET", booking_path, token=other, expected=404)
    request("DELETE", booking_path, token=other, expected=404)
    _, history = request("GET", "/api/bookings", token=owner)
    assert [item["id"] for item in history["items"]] == [booking["id"]]
    _, occupied = request("GET", f"/api/rooms?{dates}", token=owner)
    assert all(room["id"] != room_id for room in occupied)
    _, checkout_day = request(
        "GET", f"/api/rooms?startDate={end}&endDate={end + timedelta(days=1)}", token=owner
    )
    assert any(room["id"] == room_id for room in checkout_day)

    request("DELETE", booking_path, token=owner, expected=204)
    _, cancelled = request("GET", booking_path, token=owner)
    assert cancelled["status"] == "CANCELLED"
    _, free = request("GET", f"/api/rooms?{dates}", token=owner)
    assert any(room["id"] == room_id for room in free)

    barrier = Barrier(2, timeout=5)

    def try_booking(token):
        barrier.wait()
        return token, request(
            "POST", "/api/bookings", payload=payload, token=token,
            key=str(uuid4()), expected=(201, 409),
        )

    with ThreadPoolExecutor(max_workers=2) as executor:
        attempts = list(executor.map(try_booking, (owner, other)))

    assert sorted(status for _, (status, _) in attempts) == [201, 409]
    for token, (status, body) in attempts:
        if status == 201:
            request("DELETE", f"/api/bookings/{body['id']}", token=token, expected=204)
    print("OK: authentication, booking, idempotency, ownership, checkout boundary, cancellation, concurrent requests")


if __name__ == "__main__":
    main()
