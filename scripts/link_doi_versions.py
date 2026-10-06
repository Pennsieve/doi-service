#!/usr/bin/env python3
"""Link each published dataset version's DOI to the next and previous ones.

DataCite relates the versions of a dataset through related identifiers:
the older DOI gets IsPreviousVersionOf <newer>, the newer IsNewVersionOf
<older>. doi-service adds these when a version is published; this script
backfills the versions published before that.

For each dataset, the versions come from Discover's public API in version
order. Consecutive versions whose DOIs are findable at DataCite and under our
prefix are linked. Each DOI's current related identifiers are read first and
kept: only missing version links are added, so reruns change nothing.

Dry run by default: prints what it would change. --apply writes, as
doi-service's DataCite repository (DATACITE_USERNAME, DATACITE_PASSWORD; the
values of /<env>/doi-service/datacite-client-username and -password).

    ./link_doi_versions.py --dataset 514
    DATACITE_USERNAME=... DATACITE_PASSWORD=... ./link_doi_versions.py --dataset 514 --apply
    ./link_doi_versions.py --all

Dev, on DataCite's test system (/dev/doi-service/datacite-*):

    ./link_doi_versions.py --datacite https://api.test.datacite.org \
        --discover https://api.pennsieve.net/discover --prefix 10.21397 --dataset 5347
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

PREVIOUS = "IsPreviousVersionOf"
NEW = "IsNewVersionOf"


def request(method, url, body=None, auth=None, attempts=5):
    headers = {"Accept": "application/vnd.api+json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/vnd.api+json"
    if auth:
        headers["Authorization"] = "Basic " + base64.b64encode(auth.encode()).decode()
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=60) as r:
                return r.status, json.load(r)
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503, 504) and attempt < attempts:
                time.sleep(int(e.headers.get("Retry-After") or 2 ** attempt))
                continue
            return e.code, None


def discover_versions(discover, dataset_id):
    status, versions = request("GET", f"{discover}/datasets/{dataset_id}/versions")
    if status != 200:
        raise RuntimeError(f"dataset {dataset_id}: Discover answered {status}")
    return sorted(((v["version"], v.get("doi") or "") for v in versions), key=lambda v: v[0])


def datasets_with_versions(discover):
    offset, limit = 0, 100
    while True:
        status, page = request("GET", f"{discover}/datasets?limit={limit}&offset={offset}")
        if status != 200:
            raise RuntimeError(f"Discover datasets: {status}")
        for d in page["datasets"]:
            if d.get("version", 1) > 1:
                yield d["id"]
        offset += limit
        if offset >= page["totalCount"]:
            return


def related(datacite, doi):
    """A findable DOI's related identifiers, or None when it isn't findable."""
    status, body = request("GET", f"{datacite}/dois/{urllib.parse.quote(doi, safe='')}")
    if status != 200 or body["data"]["attributes"].get("state") != "findable":
        return None
    return body["data"]["attributes"].get("relatedIdentifiers") or []


def has(identifiers, relation, doi):
    return any(r.get("relationType") == relation and (r.get("relatedIdentifier") or "").lower() == doi.lower()
               for r in identifiers)


def link_dataset(datacite, discover, prefix, dataset_id, auth, apply):
    versions = [(n, doi.lower()) for n, doi in discover_versions(discover, dataset_id) if doi.lower().startswith(prefix)]
    current = {}
    for n, doi in versions:
        ids = related(datacite, doi)
        if ids is None:
            print(f"  dataset {dataset_id} v{n} {doi}: not findable at DataCite, skipped")
        else:
            current[doi] = ids
    linked = [(n, doi) for n, doi in versions if doi in current]
    additions = {}
    for (n_old, older), (n_new, newer) in zip(linked, linked[1:]):
        if not has(current[older], PREVIOUS, newer):
            additions.setdefault(older, []).append((PREVIOUS, newer, n_old, n_new))
        if not has(current[newer], NEW, older):
            additions.setdefault(newer, []).append((NEW, older, n_new, n_old))
    for doi, adds in additions.items():
        for relation, other, n, m in adds:
            print(f"  dataset {dataset_id} v{n} {doi}: + {relation} {other} (v{m})")
        if not apply:
            continue
        ids = current[doi] + [{"relatedIdentifier": other, "relatedIdentifierType": "DOI", "relationType": relation}
                              for relation, other, _, _ in adds]
        status, _ = request("PATCH", f"{datacite}/dois/{urllib.parse.quote(doi, safe='')}",
                            {"data": {"type": "dois", "attributes": {"relatedIdentifiers": ids}}}, auth)
        if status != 200:
            raise RuntimeError(f"{doi}: DataCite answered {status}")
        print(f"  dataset {dataset_id} {doi}: updated")
    return sum(len(a) for a in additions.values())


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    which = p.add_mutually_exclusive_group(required=True)
    which.add_argument("--dataset", type=int, action="append", help="a published dataset id (repeatable)")
    which.add_argument("--all", action="store_true", help="every published dataset with more than one version")
    p.add_argument("--apply", action="store_true", help="write the links (default: dry run)")
    p.add_argument("--datacite", default="https://api.datacite.org")
    p.add_argument("--discover", default="https://api.pennsieve.io/discover")
    p.add_argument("--prefix", default="10.26275", help="our DOI prefix (dev: 10.21397)")
    args = p.parse_args()

    auth = None
    if args.apply:
        user, password = os.environ.get("DATACITE_USERNAME"), os.environ.get("DATACITE_PASSWORD")
        if not user or not password:
            sys.exit("--apply needs DATACITE_USERNAME and DATACITE_PASSWORD")
        auth = f"{user}:{password}"

    ids = args.dataset or list(datasets_with_versions(args.discover))
    print(f"{'Linking' if args.apply else 'Dry run:'} {len(ids)} dataset(s)")
    total = 0
    for dataset_id in ids:
        total += link_dataset(args.datacite, args.discover, args.prefix.rstrip("/") + "/", dataset_id, auth, args.apply)
    print(f"{total} link(s) {'added' if args.apply else 'to add'}")


if __name__ == "__main__":
    main()
