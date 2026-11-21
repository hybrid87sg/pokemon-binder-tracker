"""
Test bench for card recognition: how often does the app pick the right card?

Runs on GitHub (see .github/workflows/recognition-bench.yml). It takes the
official pictures of some random cards, turns each into a fake phone photo
(tilted, glare, blur, a bit of table showing, sleeve shine), then tries
several ways of recognising it and prints a score table, e.g.

    method                         top-1   top-5
    picture model, whole list       31%     52%

The fake photos are made the same way every run (fixed random seed), so
scores can be compared between changes.
"""

import argparse
import io
import struct
import time
from concurrent.futures import ThreadPoolExecutor

import cv2
import mediapipe as mp
import numpy as np
import requests
from mediapipe.tasks.python import BaseOptions, vision
from PIL import Image

API = "https://api.tcgdex.net/v2/en"
EMBEDDINGS = "https://github.com/hybrid87sg/pokemon-binder-tracker/releases/download/card-pictures/card-embeddings.bin"
CARD_W, CARD_H = 400, 557  # a card's shape (63 x 88 mm)


# ---------- Loading ----------

def get(session, url, as_json=False):
    for attempt in range(4):
        try:
            r = session.get(url, timeout=60)
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json() if as_json else r.content
        except Exception:
            if attempt == 3:
                return None
            time.sleep(1 + attempt)


def picture(session, base_url, quality):
    raw = get(session, f"{base_url}/{quality}.webp")
    if raw is None:
        return None
    return cv2.cvtColor(np.asarray(Image.open(io.BytesIO(raw)).convert("RGB")), cv2.COLOR_RGB2BGR)


def load_embeddings(session):
    data = get(session, EMBEDDINGS)
    assert data[:4] == b"CEMB"
    _, dim, n = struct.unpack(">iii", data[4:16])
    pos, ids = 16, []
    for _ in range(n):
        (length,) = struct.unpack(">H", data[pos:pos + 2])
        ids.append(data[pos + 2:pos + 2 + length].decode())
        pos += 2 + length
    vectors = np.frombuffer(data[pos:], np.int8).reshape(n, dim).astype(np.float32)
    vectors /= np.linalg.norm(vectors, axis=1, keepdims=True)
    return ids, vectors


# ---------- Making a fake phone photo ----------

def fake_photo(card, rng, margin=None, tilt=None):
    """The card on a table, a bit tilted, with glare and blur, cut out by a frame."""
    card = cv2.resize(card, (CARD_W, CARD_H))
    margin = rng.uniform(0.03, 0.15) if margin is None else margin
    tilt = rng.uniform(0.02, 0.07) if tilt is None else tilt
    W, H = int(CARD_W * (1 + 2 * margin)), int(CARD_H * (1 + 2 * margin))
    # Table: a random colour with some texture.
    table = np.full((H, W, 3), rng.integers(30, 200, 3), np.float32)
    table += rng.normal(0, 12, (H, W, 1))
    # Where the card's corners land: roughly centred, each corner nudged (tilt).
    ox, oy = W * margin, H * margin
    corners = np.float32([[ox, oy], [ox + CARD_W, oy], [ox + CARD_W, oy + CARD_H], [ox, oy + CARD_H]])
    corners += rng.uniform(-tilt, tilt, (4, 2)).astype(np.float32) * [CARD_W, CARD_H]
    src = np.float32([[0, 0], [CARD_W, 0], [CARD_W, CARD_H], [0, CARD_H]])
    warp = cv2.getPerspectiveTransform(src, corners)
    flat = cv2.warpPerspective(card, warp, (W, H)).astype(np.float32)
    mask = cv2.warpPerspective(np.ones((CARD_H, CARD_W), np.float32), warp, (W, H))[..., None]
    photo = flat * mask + table * (1 - mask)
    # Light: brighter or darker, warmer or cooler.
    photo = photo * rng.uniform(0.7, 1.15) + rng.uniform(-15, 15, 3)
    # Glare: a soft white blob from the sleeve.
    gx, gy = rng.uniform(0.2, 0.8) * W, rng.uniform(0.2, 0.8) * H
    yy, xx = np.mgrid[0:H, 0:W]
    glare = np.exp(-(((xx - gx) / (W * 0.18)) ** 2 + ((yy - gy) / (H * 0.12)) ** 2))
    photo += glare[..., None] * rng.uniform(40, 140)
    photo = np.clip(photo, 0, 255).astype(np.uint8)
    # Slight blur and phone-camera compression.
    photo = cv2.GaussianBlur(photo, (0, 0), rng.uniform(0.5, 1.8))
    ok, jpg = cv2.imencode(".jpg", photo, [cv2.IMWRITE_JPEG_QUALITY, int(rng.integers(60, 90))])
    return cv2.imdecode(jpg, cv2.IMREAD_COLOR)


# ---------- Straightening ----------

def straighten(photo):
    """
    Finds the card's four corners and flattens it to a straight card shape.
    Returns None if no card-like shape is found.
    """
    gray = cv2.cvtColor(photo, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(gray, 40, 120)
    edges = cv2.dilate(edges, np.ones((3, 3), np.uint8))
    contours, _ = cv2.findContours(edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    area = photo.shape[0] * photo.shape[1]
    for c in sorted(contours, key=cv2.contourArea, reverse=True)[:5]:
        if cv2.contourArea(c) < area * 0.3:
            break
        quad = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
        if len(quad) != 4:
            continue
        pts = quad.reshape(4, 2).astype(np.float32)
        # Order corners: top-left, top-right, bottom-right, bottom-left.
        s, d = pts.sum(1), np.diff(pts, axis=1).ravel()
        ordered = np.float32([pts[np.argmin(s)], pts[np.argmin(d)], pts[np.argmax(s)], pts[np.argmax(d)]])
        dst = np.float32([[0, 0], [CARD_W, 0], [CARD_W, CARD_H], [0, CARD_H]])
        return cv2.warpPerspective(photo, cv2.getPerspectiveTransform(ordered, dst), (CARD_W, CARD_H))
    return None


# ---------- Detail matching ----------

ORB = cv2.ORB_create(nfeatures=1500)
MATCHER = cv2.BFMatcher(cv2.NORM_HAMMING)


def details(img):
    gray = cv2.cvtColor(cv2.resize(img, (CARD_W, CARD_H)), cv2.COLOR_BGR2GRAY)
    return ORB.detectAndCompute(gray, None)


def detail_score(a, b):
    """How many small details of picture a line up with picture b (more = same card)."""
    (ka, da), (kb, db) = a, b
    if da is None or db is None or len(ka) < 10 or len(kb) < 10:
        return 0
    good = [m for m, n in (p for p in MATCHER.knnMatch(da, db, k=2) if len(p) == 2) if m.distance < 0.75 * n.distance]
    if len(good) < 8:
        return len(good)
    src = np.float32([ka[m.queryIdx].pt for m in good])
    dst = np.float32([kb[m.trainIdx].pt for m in good])
    _, inliers = cv2.findHomography(src, dst, cv2.RANSAC, 6.0)
    return int(inliers.sum()) if inliers is not None else 0


# ---------- Bench ----------

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--cards", type=int, default=150)
    parser.add_argument("--rerank", type=int, default=20, help="candidates for detail matching")
    parser.add_argument("--summary", default="", help="file to append a Markdown table to")
    args = parser.parse_args()

    session = requests.Session()
    session.headers["User-Agent"] = "binder-tracker-recognition-bench"
    ids, vectors = load_embeddings(session)
    row = {card_id: i for i, card_id in enumerate(ids)}
    all_cards = {c["id"]: c for c in get(session, f"{API}/cards", as_json=True) if c.get("image") and c["id"] in row}
    names = {}
    for c in all_cards.values():
        names.setdefault(c["name"].lower(), []).append(c["id"])

    rng = np.random.default_rng(7)
    sample = list(rng.choice(sorted(all_cards), size=args.cards, replace=False))
    embedder = vision.ImageEmbedder.create_from_options(
        vision.ImageEmbedderOptions(base_options=BaseOptions(model_asset_path=args.model), l2_normalize=True)
    )

    def embed(bgr):
        rgb = np.ascontiguousarray(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
        v = np.array(embedder.embed(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)).embeddings[0].embedding, np.float32)
        return v / (np.linalg.norm(v) or 1)

    high_cache = {}

    def high(card_id):
        if card_id not in high_cache:
            img = picture(session, all_cards[card_id]["image"], "high")
            high_cache[card_id] = details(img) if img is not None else None
        return high_cache[card_id]

    # Download the sample's sharp pictures in parallel first.
    with ThreadPoolExecutor(16) as pool:
        originals = dict(zip(sample, pool.map(lambda i: picture(session, all_cards[i]["image"], "high"), sample)))

    methods = ["model, whole list", "model, same name", "straighten + model, whole list",
               "straighten + model, same name", f"straighten + model top {args.rerank} + detail matching",
               f"straighten + model same name + detail matching"]
    hits = {m: [0, 0] for m in methods}
    straightened_ok = 0
    tested = 0
    for n, card_id in enumerate(sample, start=1):
        original = originals.get(card_id)
        if original is None:
            continue
        tested += 1
        photo = fake_photo(original, rng)
        flat = straighten(photo)
        straightened_ok += flat is not None
        flat = flat if flat is not None else photo
        same_name = [row[i] for i in names[all_cards[card_id]["name"].lower()]]

        def rank(query, among=None):
            scores = vectors @ query if among is None else vectors[among] @ query
            order = np.argsort(-scores)
            return [ids[i] for i in order] if among is None else [ids[among[i]] for i in order]

        def score(method, ranked):
            hits[method][0] += ranked[:1] == [card_id]
            hits[method][1] += card_id in ranked[:5]

        q_photo, q_flat = embed(photo), embed(flat)
        score(methods[0], rank(q_photo))
        score(methods[1], rank(q_photo, same_name))
        score(methods[2], rank(q_flat))
        same_flat = rank(q_flat, same_name)
        score(methods[3], same_flat)

        mine = details(flat)
        with ThreadPoolExecutor(8) as pool:
            for method, candidates in ((methods[4], rank(q_flat)[: args.rerank]), (methods[5], same_flat[: args.rerank])):
                prints = list(pool.map(high, candidates))
                by_detail = sorted(zip(candidates, prints), key=lambda cp: -(detail_score(mine, cp[1]) if cp[1] else 0))
                score(method, [c for c, _ in by_detail])
        if n % 25 == 0:
            print(f"{n}/{len(sample)} cards tested", flush=True)

    embedder.close()
    lines = [
        f"Recognition bench: {tested} fake phone photos of random cards "
        f"(straightening found the card in {straightened_ok} of them).",
        "",
        "| Method | Right card first | Right card in top 5 |",
        "|---|---|---|",
    ]
    for m in methods:
        top1, top5 = hits[m]
        lines.append(f"| {m} | {100 * top1 / max(tested, 1):.0f}% | {100 * top5 / max(tested, 1):.0f}% |")
    text = "\n".join(lines)
    print(text, flush=True)
    if args.summary:
        with open(args.summary, "a") as f:
            f.write(text + "\n")


if __name__ == "__main__":
    main()
