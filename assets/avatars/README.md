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
| `kitsune` | Kitsune (nine-tailed fox) |
| `cyclops` | Cyclops |
| `mermaid` | Mermaid |
| `fairy` | Fairy |
| `genie` | Genie (djinn) |
| `golem` | Golem |
| `sphinx` | Sphinx |
| `werewolf` | Werewolf |
| `chimera` | Chimera |
| `basilisk` | Basilisk |

## Deploy

```bash
S3_BUCKET=<bucket> make avatars   # aws s3 cp --recursive assets/avatars/*.png → s3://$S3_BUCKET/avatars/
```

Needs AWS credentials (env or `~/.aws`) whose identity can `s3:PutObject` on the bucket's
`avatars/*` prefix. The runtime backend IAM user is scoped to `images/*` only, so deploying avatars
requires either extending that user's policy with `s3:PutObject` on
`arn:aws:s3:::<bucket>/avatars/*` or running with a broader deploy identity. The target uses
`cp --recursive` (not `sync`) precisely so it needs only `PutObject`, not the bucket-level
`s3:ListBucket` that `sync` requires to diff.

Served at `${CDN_BASE_URL}/avatars/<key>.png` via the same CloudFront distribution as flashcard
images. To add/replace an avatar: drop a `<key>.png` here, add the key to the backend's set
(FLA-164), and re-run `make avatars`.
