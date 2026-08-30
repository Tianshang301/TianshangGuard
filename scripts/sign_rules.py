#!/usr/bin/env python3
"""
Sign a TianshangGuard rule-set diff with the release Ed25519 private key.

Usage:
    python scripts/sign_rules.py \
        --version 1.2.3 \
        --timestamp 1700000000000 \
        --nonce "$(python -c 'import secrets;print(secrets.token_hex(16))')" \
        --adds adds.json --removes removes.json \
        > diff.json

The canonical payload signed MUST match SignatureVerifier.canonicalize() exactly:

    version + "\\n" + timestamp + "\\n" + nonce + "\\n" +
    "\\n".join(sorted(adds)) + "\\n" + "\\n".join(sorted(removes))

Output is a JSON object matching com.tianshang.guard.data.remote.RulesDiff.

The private key (scripts/rules_signing_key.pem) MUST NOT be committed to the
repository and is listed in .gitignore. Keep it only on the release signer host.
"""
import argparse
import base64
import json
import sys

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization


def canonicalize(version: str, timestamp: int, nonce: str, adds: list, removes: list) -> bytes:
    # Domain lists are sorted so the canonical form is independent of the order
    # supplied on the signing host (must match SignatureVerifier.canonicalize).
    payload = "\n".join([
        version,
        str(timestamp),
        nonce,
        "\n".join(sorted(adds)),
        "\n".join(sorted(removes)),
    ])
    return payload.encode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Sign a TianshangGuard rules diff")
    parser.add_argument("--key", default="scripts/rules_signing_key.pem",
                        help="Ed25519 private key PEM path")
    parser.add_argument("--version", required=True)
    parser.add_argument("--timestamp", required=True, type=int)
    parser.add_argument("--nonce", required=True)
    parser.add_argument("--adds", required=True, help="JSON file with a list of domain strings")
    parser.add_argument("--removes", required=True, help="JSON file with a list of domain strings")
    args = parser.parse_args()

    with open(args.adds, "r", encoding="utf-8") as f:
        adds = json.load(f)
    with open(args.removes, "r", encoding="utf-8") as f:
        removes = json.load(f)

    with open(args.key, "rb") as f:
        priv = serialization.load_pem_private_key(f.read(), password=None)

    if not isinstance(priv, Ed25519PrivateKey):
        print("ERROR: key is not an Ed25519 private key", file=sys.stderr)
        return 2

    canonical = canonicalize(args.version, args.timestamp, args.nonce, adds, removes)
    signature = priv.sign(canonical)
    sig_b64 = base64.b64encode(signature).decode("ascii")

    diff = {
        "version": args.version,
        "timestamp": args.timestamp,
        "nonce": args.nonce,
        "adds": adds,
        "removes": removes,
        "signature": sig_b64,
    }
    json.dump(diff, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
