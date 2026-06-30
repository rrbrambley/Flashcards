# Profile avatars

The curated set of profile avatars (FLA-162) — whimsical mythical beasts. Users **pick** one;
they never upload (a predefined set keeps the profile-image surface moderation-free).

These are the repo-versioned **source** images (512×512 PNG, transparent background). They're
**served from the CDN**, not bundled in the apps: the backend stores a per-user `avatarKey` and
resolves `${CDN_BASE_URL}/avatars/<key>.png`; clients render that URL and pick from the backend's
`GET /avatars` catalog. The filename (minus `.png`) **is the key**.

| Key | Beast |
|-----|-------|
| `dragon` | Dragon |
| `yeti` | Yeti |
| `kraken` | Kraken |
| `phoenix` | Phoenix |
| `unicorn` | Unicorn |
| `griffin` | Griffin |
| `nessie` | Loch Ness Monster |
| `minotaur` | Minotaur |
| `cerberus` | Cerberus |
| `pegasus` | Pegasus |

## Deploy

```bash
make avatars      # aws s3 sync assets/avatars/ → s3://$S3_BUCKET/avatars/ (needs AWS creds)
```

Served at `${CDN_BASE_URL}/avatars/<key>.png` via the same CloudFront distribution as flashcard
images. To add/replace an avatar: drop a `<key>.png` here, add the key to the backend's set
(FLA-164), and re-run `make avatars`.
