#!/usr/bin/env python3
"""Fill in the DataCite metadata doi-service now sends for published versions.

For every published dataset version, from Discover's public API, the DOI gets:

  - subjects: the dataset's tags (other subjects are kept);
  - sizes: the version's size and file count, e.g. "3.2 TB", "1,355 files";
  - dates: Issued (the version's publication day), Available (when an embargo
    ended) and Updated (the last revision), replacing those types and keeping
    other dates;
  - the SPDX identifier of each licence whose URL is an spdx.org licence.

Only DOIs findable at DataCite under our prefix are changed, and only the
attributes that differ are sent, so reruns change nothing. Updating a DOI also
makes DataCite refresh its search index entry (what Commons shows), usage
included.

Dry run by default: prints what it would change. --apply writes, as
doi-service's DataCite repository (DATACITE_USERNAME, DATACITE_PASSWORD).

    ./update_doi_metadata.py --dataset 514
    ./update_doi_metadata.py --all
Dev (DataCite's test system):
    ./update_doi_metadata.py --datacite https://api.test.datacite.org \\
        --discover https://api.pennsieve.net/discover --prefix 10.21397 --dataset 5347
"""
import argparse
import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SPDX = re.compile(r"^https?://spdx\.org/licenses/(.+?)(?:\.json|\.html)?$")
UNITS = ["bytes", "kB", "MB", "GB", "TB", "PB"]


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


def human_bytes(size):
    value, unit = float(size), 0
    while value >= 1000 and unit < len(UNITS) - 1:
        value /= 1000
        unit += 1
    return f"{size} bytes" if unit == 0 else f"{value:.1f} {UNITS[unit]}"


def files(count):
    return f"{count:,} {'file' if count == 1 else 'files'}"


def day(timestamp):
    return timestamp[:10] if timestamp else None


def wanted(version, attributes):
    """The attributes to change, from Discover's version and DataCite's record."""
    changes = {}

    subjects = attributes.get("subjects") or []
    have = {s.get("subject") for s in subjects}
    tags = []
    for t in version.get("tags") or []:
        t = t.strip()
        if t and t not in have and t not in tags:
            tags.append(t)
    if tags:
        changes["subjects"] = subjects + [{"subject": t} for t in tags]

    sizes = []
    if version.get("size") is not None:
        sizes.append(human_bytes(version["size"]))
    if version.get("fileCount") is not None:
        sizes.append(files(version["fileCount"]))
    if sizes and sizes != (attributes.get("sizes") or []):
        changes["sizes"] = sizes

    given = {"Issued": day(version.get("versionPublishedAt")),
             "Available": day(version.get("embargoReleaseDate")) if version.get("embargoReleaseDate") else None,
             "Updated": day(version.get("revisedAt"))}
    given = {k: v for k, v in given.items() if v}
    dates = attributes.get("dates") or []
    merged = [d for d in dates if d.get("dateType") not in given] + [{"date": v, "dateType": k} for k, v in given.items()]
    if sorted((d["dateType"], d["date"]) for d in merged) != sorted((d.get("dateType"), d.get("date")) for d in dates):
        changes["dates"] = merged

    rights = attributes.get("rightsList") or []
    updated_rights = []
    for r in rights:
        m = SPDX.match(r.get("rightsUri") or "")
        if m and not r.get("rightsIdentifier"):
            r = dict(r, rightsIdentifier=m.group(1), rightsIdentifierScheme="SPDX", schemeUri="https://spdx.org/licenses/")
        updated_rights.append(r)
    if updated_rights != rights:
        changes["rightsList"] = updated_rights
    return changes


def versions(discover, dataset_id):
    status, body = request("GET", f"{discover}/datasets/{dataset_id}/versions")
    if status != 200:
        raise RuntimeError(f"dataset {dataset_id}: Discover answered {status}")
    return body


def all_datasets(discover):
    offset, limit = 0, 100
    while True:
        status, page = request("GET", f"{discover}/datasets?limit={limit}&offset={offset}")
        if status != 200:
            raise RuntimeError(f"Discover datasets: {status}")
        for d in page["datasets"]:
            yield d["id"]
        offset += limit
        if offset >= page["totalCount"]:
            return


def update_dataset(datacite, discover, prefix, dataset_id, auth, apply):
    changed = 0
    for version in versions(discover, dataset_id):
        doi = (version.get("doi") or "").lower()
        if not doi.startswith(prefix):
            continue
        url = f"{datacite}/dois/{urllib.parse.quote(doi, safe='')}"
        status, body = request("GET", url)
        if status != 200 or body["data"]["attributes"].get("state") != "findable":
            print(f"  dataset {dataset_id} v{version['version']} {doi}: not findable at DataCite, skipped")
            continue
        changes = wanted(version, body["data"]["attributes"])
        if not changes:
            continue
        changed += 1
        summary = ", ".join(f"{k}: {json.dumps(v)[:80]}" for k, v in changes.items())
        print(f"  dataset {dataset_id} v{version['version']} {doi}: {summary}")
        if apply:
            status, _ = request("PATCH", url, {"data": {"type": "dois", "attributes": changes}}, auth)
            if status != 200:
                raise RuntimeError(f"{doi}: DataCite answered {status}")
    return changed


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    which = p.add_mutually_exclusive_group(required=True)
    which.add_argument("--dataset", type=int, action="append", help="a published dataset id (repeatable)")
    which.add_argument("--all", action="store_true", help="every published dataset")
    p.add_argument("--apply", action="store_true", help="write the changes (default: dry run)")
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

    ids = args.dataset or list(all_datasets(args.discover))
    print(f"{'Updating' if args.apply else 'Dry run:'} {len(ids)} dataset(s)")
    total = 0
    for dataset_id in ids:
        total += update_dataset(args.datacite, args.discover, args.prefix.rstrip("/") + "/", dataset_id, auth, args.apply)
    print(f"{total} DOI(s) {'updated' if args.apply else 'to update'}")


if __name__ == "__main__":
    main()
