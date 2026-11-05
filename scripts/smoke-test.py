"""Exercise the booking API through a running Gateway and demo catalog."""

import json
import os
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from threading import Barrier
from time import monotonic, sleep
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import UUID, uuid4


BASE_URL = os.environ.get("GATEWAY_URL", "http://localhost:8080").rstrip("/")


def request(method, path, *, payload=None, token=None, key=None, expected=200, base=BASE_URL):
    headers = {"Accept": "application/json"}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if key:
        headers["Idempotency-Key"] = key

    req = Request(base + path, data=body, headers=headers, method=method)
    try:
        response = urlopen(req, timeout=25)
    except HTTPError as error:
        response = error

    with response:
        result = response.read()
        statuses = (expected,) if isinstance(expected, int) else expected
        if response.status not in statuses:
            raise AssertionError(f"{method} {path} returned {response.status}: {result.decode()}")
        if base == BASE_URL:
            UUID(response.headers["X-Trace-Id"])
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
    request("POST", "/api/bookings", token=owner, key=key, expected=409,
            payload={**payload, "endDate": str(end + timedelta(days=1))})

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
    check_auto_select(owner, room_id, start, end)
    if os.environ.get("HOTEL_BOOKING_DEMO_ADMIN_PASSWORD"):
        check_admin_and_occupancy(owner, start)
    if proxy_url := os.environ.get("HOTEL_FAULT_PROXY_URL"):
        check_lost_reply(owner, proxy_url, room_id, start, end)
    check_documentation()


def check_auto_select(owner, room_id, start, end):
    dates = {"startDate": str(start), "endDate": str(end)}
    first_key, next_key = str(uuid4()), str(uuid4())
    _, first = request("POST", "/api/bookings", token=owner, key=first_key, expected=201,
                       payload={"autoSelect": True, "roomId": -1, **dates})
    assert first["roomId"] == room_id
    recommendation_dates = f"startDate={end}&endDate={end + timedelta(days=1)}"
    _, recommendations = request("GET", f"/api/rooms/recommend?{recommendation_dates}", token=owner)
    assert recommendations[0]["id"] != room_id and recommendations[-1]["id"] == room_id
    _, second = request("POST", "/api/bookings", token=owner, key=next_key, expected=201,
                        payload={"autoSelect": True, **dates})
    assert second["roomId"] != first["roomId"]
    _, repeated = request("POST", "/api/bookings", token=owner, key=first_key, expected=201,
                          payload={"autoSelect": True, **dates})
    assert repeated["id"] == first["id"]
    for booking in (first, second):
        request("DELETE", f"/api/bookings/{booking['id']}", token=owner, expected=204)
    print("OK: automatic selection, stable tie-breaking and idempotency")


def check_admin_and_occupancy(owner, start):
    _, login = request("POST", "/api/auth/login", payload={
        "username": "demo-admin", "password": os.environ["HOTEL_BOOKING_DEMO_ADMIN_PASSWORD"]
    })
    admin = login["accessToken"]
    request("GET", "/api/users", token=owner, expected=403)
    request("POST", "/api/hotels", token=owner, payload={}, expected=403)
    _, user = request("POST", "/api/users", token=admin, expected=201, payload={
        "username": f"admin-created-{uuid4()}", "password": str(uuid4()), "role": "USER"
    })
    user_path = f"/api/users/{user['id']}"
    _, updated = request("PATCH", user_path, token=admin, payload={"username": f"updated-{uuid4()}"})
    _, fetched = request("GET", user_path, token=admin)
    assert fetched == updated and "password" not in fetched
    request("DELETE", user_path, token=admin, expected=204)

    _, hotel = request("POST", "/api/hotels", token=admin, expected=201, payload={
        "name": "Acceptance Hotel", "address": "Test Street 1"
    })
    hotel_path = f"/api/hotels/{hotel['id']}"
    request("PUT", hotel_path, token=admin, payload={"name": "Updated Hotel", "address": "Test Street 2"})
    _, fetched = request("GET", hotel_path, token=owner)
    assert fetched["name"] == "Updated Hotel"
    rooms = [
        request("POST", "/api/rooms", token=admin, expected=201,
                payload={"hotelId": hotel["id"], "number": str(number), "available": True})[1]
        for number in (301, 302)
    ]
    room_path = f"/api/rooms/{rooms[0]['id']}"
    request("PUT", room_path, token=admin, payload={"number": "303", "available": True})
    assert request("GET", room_path, token=owner)[1]["number"] == "303"
    request("DELETE", hotel_path, token=admin, expected=409)

    reservations = [(rooms[0], 0, 8), *[(rooms[1], day, day + 1) for day in (1, 3, 5)]]
    bookings = []
    for room, first, last in reservations:
        payload = {"roomId": room["id"], "startDate": str(start + timedelta(days=first)),
                   "endDate": str(start + timedelta(days=last))}
        bookings.append(request("POST", "/api/bookings", token=owner, key=str(uuid4()),
                                payload=payload, expected=201)[1])
    request("DELETE", room_path, token=admin, expected=409)

    params = f"startDate={start}&endDate={start + timedelta(days=10)}&hotelId={hotel['id']}"
    path = "/api/rooms/occupancy?" + params
    request("GET", path, token=owner, expected=403)
    _, stats = request("GET", path, token=admin)
    assert [(room["bookings"], room["occupiedNights"], room["occupancyPercent"]) for room in stats] == [
        (1, 8, 80.0), (3, 3, 30.0)
    ]
    clipped = path.replace(f"startDate={start}", f"startDate={start + timedelta(days=4)}")
    _, partial = request("GET", clipped, token=admin)
    assert [room["occupiedNights"] for room in partial] == [4, 1]
    for booking in bookings:
        request("DELETE", f"/api/bookings/{booking['id']}", token=owner, expected=204)
    _, free_stats = request("GET", path, token=admin)
    assert all(room["occupiedNights"] == 0 for room in free_stats)
    for room in rooms:
        request("DELETE", f"/api/rooms/{room['id']}", token=admin, expected=204)
    request("DELETE", hotel_path, token=admin, expected=204)
    print("OK: admin permissions, user and catalog CRUD, 80% vs 30% occupancy, clipping and cancellation")


def check_lost_reply(owner, proxy_url, room_id, start, end):
    request("POST", "/__test/drop-confirmations", base=proxy_url, expected=204)
    key = str(uuid4())
    payload = {"roomId": room_id, "startDate": str(start), "endDate": str(end)}
    request("POST", "/api/bookings", token=owner, key=key, payload=payload, expected=503)
    faults = request("GET", "/__test/status", base=proxy_url)[1]
    assert faults["dropped"] == 3 and faults["blockedReleases"] >= 3
    _, recovered = request("POST", "/api/bookings", token=owner, key=key, payload=payload)
    assert recovered["status"] == "CANCELLED"
    availability = f"/api/rooms?startDate={start}&endDate={end}"
    _, rooms = request("GET", availability, token=owner)
    assert all(room["id"] != room_id for room in rooms)
    request("POST", "/__test/allow-releases", base=proxy_url, expected=204)
    deadline = monotonic() + 10
    while monotonic() < deadline:
        _, rooms = request("GET", availability, token=owner)
        if any(room["id"] == room_id for room in rooms):
            break
        sleep(0.2)
    else:
        raise AssertionError("Background compensation did not release the room")
    _, next_booking = request("POST", "/api/bookings", token=owner, key=str(uuid4()),
                              payload=payload, expected=201)
    request("DELETE", f"/api/bookings/{next_booking['id']}", token=owner, expected=204)
    print("OK: Hotel committed, three replies timed out, failed release was retried in background, room was rebooked")


def check_documentation():
    for env, required_path in (("BOOKING_URL", "/api/bookings"), ("HOTEL_URL", "/api/rooms/occupancy")):
        if base := os.environ.get(env):
            _, spec = request("GET", "/v3/api-docs", base=base)
            assert required_path in spec["paths"]
            assert all(not path.endswith(("/release", "/confirm-availability")) for path in spec["paths"])
    print("OK: OpenAPI documents available")


if __name__ == "__main__":
    main()
