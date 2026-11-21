"""
Builds card-embeddings.bin: a "picture description" for every card in TCGdex.

Runs on GitHub (see .github/workflows/card-pictures.yml), not on the phone.
For each card it downloads the official picture, runs the same small AI
picture model the app uses (app/src/main/assets/card_embedder.tflite), and
saves the 1024 numbers it produces. The app downloads this file once and
compares a scanned card against every card with it, without the internet.

File layout (all numbers big-endian):
  4 bytes  b"CEMB"
  int32    format version (1)
  int32    numbers per card (1024)
  int32    card count N
  N times: uint16 id length, id as UTF-8 (e.g. "swsh3-136")
  N * 1024 int8 values: each card's numbers, scaled so the biggest is +-127
"""

import argparse
import io
import struct
import sys
import time
from concurrent.futures import ThreadPoolExecutor

import mediapipe as mp
import numpy as np
import requests
from mediapipe.tasks.python import BaseOptions, vision
from PIL import Image

API = "https://api.tcgdex.net/v2/en"
SKIP_SERIES = {"tcgp"}  # Pokémon TCG Pocket (the phone game) isn't physical cards


def fetch_json(session, url):
    for attempt in range(5):
        try:
            r = session.get(url, timeout=60)
            r.raise_for_status()
            return r.json()
        except Exception:
            if attempt == 4:
                raise
            time.sleep(2 ** attempt)


def card_list(session):
    """Every card with a picture, skipping TCG Pocket sets."""
    skip_sets = set()
    for series in fetch_json(session, f"{API}/series"):
        if series["id"] in SKIP_SERIES:
            details = fetch_json(session, f"{API}/series/{series['id']}")
            skip_sets.update(s["id"] for s in details.get("sets", []))
    cards = []
    for c in fetch_json(session, f"{API}/cards"):
        image = c.get("image")
        set_id = c["id"].rsplit("-", 1)[0]
        if image and set_id not in skip_sets:
            cards.append((c["id"], image + "/low.webp"))
    return cards


def download(session, url):
    for attempt in range(3):
        try:
            r = session.get(url, timeout=60)
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return Image.open(io.BytesIO(r.content)).convert("RGB")
        except Exception:
            if attempt == 2:
                return None
            time.sleep(1 + attempt)


def to_int8(vector):
    v = np.asarray(vector, dtype=np.float32)
    peak = float(np.max(np.abs(v))) or 1.0
    return np.clip(np.round(v / peak * 127), -127, 127).astype(np.int8)


def write_file(path, ids, vectors, dim):
    with open(path, "wb") as f:
        f.write(b"CEMB")
        f.write(struct.pack(">iii", 1, dim, len(ids)))
        for card_id in ids:
            raw = card_id.encode("utf-8")
            f.write(struct.pack(">H", len(raw)))
            f.write(raw)
        for v in vectors:
            f.write(v.tobytes())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--limit", type=int, default=0, help="only the first N cards (for testing)")
    args = parser.parse_args()

    embedder = vision.ImageEmbedder.create_from_options(
        vision.ImageEmbedderOptions(base_options=BaseOptions(model_asset_path=args.model), l2_normalize=True)
    )
    session = requests.Session()
    session.headers["User-Agent"] = "binder-tracker-card-pictures"
    cards = card_list(session)
    if args.limit:
        cards = cards[: args.limit]
    print(f"{len(cards)} cards with pictures", flush=True)

    ids, vectors, dim = [], [], 0
    with ThreadPoolExecutor(max_workers=16) as pool:
        pictures = pool.map(lambda card: download(session, card[1]), cards)
        for n, ((card_id, _), picture) in enumerate(zip(cards, pictures), start=1):
            if picture is not None:
                result = embedder.embed(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(np.asarray(picture))))
                vector = result.embeddings[0].embedding
                dim = len(vector)
                ids.append(card_id)
                vectors.append(to_int8(vector))
            if n % 1000 == 0:
                print(f"{n}/{len(cards)} done", flush=True)

    embedder.close()
    if len(ids) < len(cards) * 0.9:
        sys.exit(f"Only {len(ids)} of {len(cards)} pictures worked; not publishing.")
    write_file(args.out, ids, vectors, dim)
    print(f"Wrote {len(ids)} cards x {dim} numbers to {args.out}", flush=True)


if __name__ == "__main__":
    main()
