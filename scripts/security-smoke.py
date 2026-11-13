"""Run lightweight security regression checks against a deployed MyGameList."""

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", help="Public HTTPS origin")
    parser.add_argument(
        "--check-rate-limit",
        action="store_true",
        help="Send six invalid requests. This briefly consumes the caller IP's auth quota.",
    )
    args = parser.parse_args()
    url = urlsplit(args.url)
    if (
        url.scheme != "https"
        or not url.hostname
        or url.username is not None
        or url.path not in ("", "/")
        or url.query
        or url.fragment
    ):
        parser.error("url must be an HTTPS origin without credentials, path or query")

    origin = f"https://{url.netloc}"
    opener = build_opener(NoRedirect())
    failures = []

    def request(path, *, method="GET", headers=None, body=None, base=origin):
        req = Request(base + path, method=method, headers=headers or {}, data=body)
        try:
            response = opener.open(req, timeout=8)
        except HTTPError as exc:
            response = exc
        with response:
            return response.status, response.headers, response.read(65536)

    def check(name, condition):
        print(f"{'PASS' if condition else 'FAIL'} {name}")
        if not condition:
            failures.append(name)

    def post(path):
        return request(path, method="POST", headers={"Content-Type": "application/json"}, body=b"{}")

    path = "/login?next=probe"
    status, headers, _ = request(path, base=f"http://{url.hostname}")
    check("HTTP redirects to HTTPS and preserves the URL", status == 308 and headers.get("Location") == origin + path)

    status, headers, _ = request("/login")
    csp = headers.get("Content-Security-Policy", "")
    check(
        "Login HTML has security headers",
        status == 200
        and headers.get_content_type() == "text/html"
        and headers.get("X-Frame-Options") == "DENY"
        and headers.get("X-Content-Type-Options") == "nosniff"
        and "frame-ancestors 'none'" in csp.split("; "),
    )

    for path in ("/api/auth/me", "/api/me/games"):
        status, headers, _ = request(path)
        check(f"{path} denies anonymous access", status == 401 and headers.get_content_type() == "application/problem+json")

    status, headers, _ = request("/api/auth/csrf")
    cookies = headers.get_all("Set-Cookie", [])
    check("CSRF cookie uses Secure", status == 200 and any(
        cookie.startswith("XSRF-TOKEN=")
        and "secure" in [part.strip().lower() for part in cookie.split(";")[1:]]
        for cookie in cookies
    ))

    status, headers, _ = request("/api/auth/login", method="OPTIONS", headers={
        "Origin": "https://security-probe.invalid",
        "Access-Control-Request-Method": "POST",
    })
    check("Foreign origin cannot perform credentialed CORS", status == 403 and not headers.get("Access-Control-Allow-Origin"))

    for path in ("/api/auth/login", "/api/users"):
        status, headers, _ = post(path)
        check(f"{path} rejects missing CSRF", status == 403 and headers.get_content_type() == "application/problem+json")

    status, headers, body = request("/api/games?query=%27%20OR%20%271%27%3D%271")
    check("Search treats an injection probe as a literal term", status == 200
          and headers.get_content_type() == "application/json"
          and json.loads(body).get("content") == [])

    status, headers, _ = request("/v3/api-docs")
    check("OpenAPI JSON is not served publicly", status in (401, 403, 404)
          or (status == 200 and headers.get_content_type() == "text/html"))

    if args.check_rate_limit:
        statuses = [post("/api/auth/login" if i % 2 == 0 else "/api/users")[0] for i in range(6)]
        check("Login and registration share a quota", all(s in (403, 429) for s in statuses) and 429 in statuses)

    return int(bool(failures))


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (URLError, TimeoutError, ValueError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        sys.exit(1)
