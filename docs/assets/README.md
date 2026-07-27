# README assets

Images referenced by the root `README.md`.

## `demo.gif` — product demo (Image 1, currently in the README)

Animated capture of the full flow: upload an invoice → savings card → question → grounded
answer with citations. 960×456, 146 frames, 23.5 s, ~550 KB. Rendered at `width="840"` in the README.

How to record it:

1. Start the app: `docker compose --profile local-ai up -d` (see [`../DOCKER.md`](../DOCKER.md)).
2. Open **http://localhost:8082/chat/**.
3. Record the viewport (≈1280 px wide looks best) while uploading a sample electricity
   invoice PDF, waiting for the savings card, and asking one question so a citation chip appears.
4. Export as GIF and save it here as `demo.gif`.

Before committing a new recording, check the frame delays. Browsers clamp any frame under
20 ms to 100 ms, so a recorder that writes sub-20 ms delays plays back at a duration nobody
chose — the nominal length of the file and what GitHub actually shows drift apart. Target
≥ 80 ms per frame, hold the last frame ~2 s so the citation chips stay readable before the
loop restarts, and downscale to ~960 px wide to keep the file under 1 MB. Quantize to a single
shared palette **without** dithering: dither noise changes every frame and defeats the
inter-frame delta compression, which makes the output larger than the source.