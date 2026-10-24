#!/usr/bin/env python3
"""Run the redeem saga against a local Compose stack."""

LEDGER = "http://localhost:8091"
FULFILLMENT = "http://localhost:8092"
MGMT = "http://localhost:15674"
AUTH = "platform:platform"

import base64
import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def call(method, url, body=None, auth=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        request.add_header("content-type", "application/json")
    if auth:
        token = base64.b64encode(auth.encode()).decode()
        request.add_header("Authorization", "Basic " + token)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            raw = response.read().decode()
            parsed = json.loads(raw) if raw else None
            return response.status, parsed
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return ex.code, parsed


def wait_ok(url, seconds=180, auth=None):
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            status, _ = call("GET", url, auth=auth)
            if status == 200:
                print(f"up  {url}")
                return
        except Exception:
            pass
        time.sleep(2)
    sys.exit(f"timed out waiting for {url}")


def until(label, fn, seconds=45):
    deadline = time.time() + seconds
    last = None
    while time.time() < deadline:
        last = fn()
        if last is not None:
            return last
        time.sleep(1)
    sys.exit(f"{label} failed, last={last!r}")


def queue_depth(name):
    status, body = call("GET", f"{MGMT}/api/queues/%2F/{name}", auth=AUTH)
    if status != 200 or not isinstance(body, dict):
        return 0
    return body.get("messages", 0)


def main():
    wait_ok(f"{LEDGER}/actuator/health")
    wait_ok(f"{FULFILLMENT}/actuator/health")
    wait_ok(f"{MGMT}/api/overview", seconds=60, auth=AUTH)

    status, member = call("POST", f"{LEDGER}/members", {"name": "Ana"})
    if status != 201:
        sys.exit(f"create member -> {status} {member}")
    member_id = member["id"]

    status, earned = call("POST", f"{LEDGER}/members/{member_id}/earn", {"points": 100})
    if status != 200 or earned.get("balance") != 100:
        sys.exit(f"earn -> {status} {earned}")

    status, coffee = call(
        "POST",
        f"{LEDGER}/members/{member_id}/redeem",
        {"points": 40, "rewardCode": "COFFEE"},
    )
    if status != 200 or coffee.get("balance") != 60:
        sys.exit(f"redeem coffee -> {status} {coffee}")
    coffee_id = coffee["commandId"]

    def coffee_done():
        code, body = call("GET", f"{FULFILLMENT}/fulfillments/{coffee_id}")
        if code == 200 and isinstance(body, dict) and body.get("status") == "SUCCESS":
            return body
        return None

    until("coffee fulfillment", coffee_done)
    _, current = call("GET", f"{LEDGER}/members/{member_id}")
    if current.get("balance") != 60:
        sys.exit(f"balance after coffee {current}")

    status, stock = call(
        "POST",
        f"{LEDGER}/members/{member_id}/redeem",
        {"points": 10, "rewardCode": "OUT_OF_STOCK"},
    )
    if status != 200 or stock.get("balance") != 50:
        sys.exit(f"redeem stock -> {status} {stock}")
    stock_id = stock["commandId"]

    def compensated():
        _, body = call("GET", f"{LEDGER}/members/{member_id}")
        if isinstance(body, dict) and body.get("balance") == 60:
            return body
        return None

    until("compensation", compensated, seconds=60)
    depth = until(
        "fulfillment dead letter",
        lambda: count if (count := queue_depth("reward.fulfill.dlq")) >= 1 else None,
    )
    code, missing = call("GET", f"{FULFILLMENT}/fulfillments/{stock_id}")
    if code != 404:
        sys.exit(f"out-of-stock fulfillment should be absent, got {code} {missing}")

    status, racer = call("POST", f"{LEDGER}/members", {"name": "Racer"})
    if status != 201:
        sys.exit(f"create racer -> {status} {racer}")
    racer_id = racer["id"]

    def earn_one(_):
        return call("POST", f"{LEDGER}/members/{racer_id}/earn", {"points": 1})

    with ThreadPoolExecutor(max_workers=20) as pool:
        earns = list(pool.map(earn_one, range(20)))
    if [status for status, _ in earns].count(200) != 20:
        sys.exit(f"concurrent earns -> {earns}")

    def raced():
        _, body = call("GET", f"{LEDGER}/members/{racer_id}")
        if isinstance(body, dict) and body.get("balance") == 20:
            return body
        return None

    until("concurrent earns", raced, seconds=20)

    status, guarded = call("POST", f"{LEDGER}/members", {"name": "Guarded"})
    if status != 201:
        sys.exit(f"create guarded -> {status} {guarded}")
    guarded_id = guarded["id"]
    status, seeded = call("POST", f"{LEDGER}/members/{guarded_id}/earn", {"points": 20})
    if status != 200 or seeded.get("balance") != 20:
        sys.exit(f"seed guarded -> {status} {seeded}")

    def redeem_ten(_):
        return call(
            "POST",
            f"{LEDGER}/members/{guarded_id}/redeem",
            {"points": 10, "rewardCode": "COFFEE"},
        )

    with ThreadPoolExecutor(max_workers=5) as pool:
        redeems = list(pool.map(redeem_ten, range(5)))
    codes = [status for status, _ in redeems]
    if codes.count(200) != 2 or codes.count(409) != 3:
        sys.exit(f"overlapping redeems -> {codes}")
    _, guarded_now = call("GET", f"{LEDGER}/members/{guarded_id}")
    if guarded_now.get("balance") != 0:
        sys.exit(f"guarded balance -> {guarded_now}")

    print(f"loyalty ledger smoke passed (dlq={depth})")


if __name__ == "__main__":
    main()
