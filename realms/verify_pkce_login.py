#!/usr/bin/env python3
import argparse
import base64
import hashlib
import http.cookiejar
import json
import secrets
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser


REALM = "spring-boot-microservices-realm"
CLIENT_ID = "gap-pos"
REDIRECT_URI = "http://localhost:5173/"


class LoginFormParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.action = None
        self.inputs = {}

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == "form" and self.action is None:
            self.action = attributes.get("action")
        if tag == "input" and attributes.get("name"):
            self.inputs[attributes["name"]] = attributes.get("value", "")


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


class LocalhostCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    def return_ok_secure(self, cookie, request):
        host = urllib.parse.urlparse(request.get_full_url()).hostname
        if cookie.secure and host in ("localhost", "127.0.0.1"):
            return True
        return super().return_ok_secure(cookie, request)


def opener_for_session():
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(
            http.cookiejar.CookieJar(policy=LocalhostCookiePolicy())
        ),
        NoRedirectHandler(),
    )


def open_response(opener, request):
    try:
        return opener.open(request)
    except urllib.error.HTTPError as response:
        return response


def authorization_url(base, redirect_uri, state, challenge):
    parameters = {
        "client_id": CLIENT_ID,
        "redirect_uri": redirect_uri,
        "response_type": "code",
        "scope": "openid",
        "state": state,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    }
    return (
        f"{base.rstrip('/')}/realms/{REALM}/protocol/openid-connect/auth?"
        f"{urllib.parse.urlencode(parameters)}"
    )


def code_challenge(verifier):
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    return base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")


def get_authorization_code(base, username, password, include_verifier=True,
                           redirect_uri=REDIRECT_URI):
    opener = opener_for_session()
    verifier = secrets.token_urlsafe(64)
    state = secrets.token_urlsafe(24)
    request = urllib.request.Request(
        authorization_url(base, redirect_uri, state, code_challenge(verifier)),
        headers={"Accept": "text/html"},
    )
    response = open_response(opener, request)
    page = response.read().decode("utf-8", errors="replace")
    if response.status != 200:
        raise RuntimeError(
            f"Authorization page returned HTTP {response.status}: {page[:500]}"
        )

    parser = LoginFormParser()
    parser.feed(page)
    if not parser.action:
        raise RuntimeError("Could not find the Keycloak login form action")
    form_url = urllib.parse.urljoin(response.geturl(), parser.action)
    fields = parser.inputs
    fields["username"] = username
    fields["password"] = password
    login_request = urllib.request.Request(
        form_url,
        data=urllib.parse.urlencode(fields).encode("utf-8"),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    login_response = open_response(opener, login_request)
    location = login_response.headers.get("Location")
    if login_response.status not in (302, 303) or not location:
        body = login_response.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Login did not redirect to the application "
            f"(HTTP {login_response.status}): {body[:500]}"
        )

    callback = urllib.parse.urlparse(urllib.parse.urljoin(base, location))
    parameters = urllib.parse.parse_qs(callback.query)
    code = parameters.get("code", [None])[0]
    returned_state = parameters.get("state", [None])[0]
    if callback.scheme + "://" + callback.netloc + callback.path != redirect_uri:
        raise RuntimeError(f"Unexpected authorization redirect: {location}")
    if not code or returned_state != state:
        raise RuntimeError("Authorization response omitted the code or had invalid state")
    return code, verifier if include_verifier else None


def exchange_code(base, code, verifier):
    parameters = {
        "grant_type": "authorization_code",
        "client_id": CLIENT_ID,
        "code": code,
        "redirect_uri": REDIRECT_URI,
    }
    if verifier is not None:
        parameters["code_verifier"] = verifier
    request = urllib.request.Request(
        f"{base.rstrip('/')}/realms/{REALM}/protocol/openid-connect/token",
        data=urllib.parse.urlencode(parameters).encode("utf-8"),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    response = open_response(opener_for_session(), request)
    body = response.read().decode("utf-8", errors="replace")
    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        payload = {"raw": body}
    return response.status, payload


def decode_access_token(access_token):
    payload = access_token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))


def check_discovery(base):
    url = f"{base.rstrip('/')}/realms/{REALM}/.well-known/openid-configuration"
    with urllib.request.urlopen(url) as response:
        discovery = json.loads(response.read())
    if response.status != 200 or not discovery.get("authorization_endpoint"):
        raise RuntimeError("The Keycloak OpenID discovery document is incomplete")
    return url


def check_unregistered_redirect(base):
    verifier = secrets.token_urlsafe(64)
    url = authorization_url(
        base, "http://evil.example/", secrets.token_urlsafe(24), code_challenge(verifier)
    )
    response = open_response(opener_for_session(), urllib.request.Request(url))
    body = response.read().decode("utf-8", errors="replace")
    rejected = response.status >= 400 or "invalid parameter: redirect_uri" in body.lower()
    if not rejected:
        raise RuntimeError(
            f"Keycloak did not reject the unregistered redirect (HTTP {response.status})"
        )
    return response.status


def main():
    parser = argparse.ArgumentParser(description="Verify Keycloak login and PKCE claims")
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--user", required=True)
    parser.add_argument("--password", required=True)
    args = parser.parse_args()

    code, verifier = get_authorization_code(args.base, args.user, args.password)
    status, tokens = exchange_code(args.base, code, verifier)
    if status != 200 or "access_token" not in tokens:
        raise RuntimeError(f"PKCE token exchange failed (HTTP {status}): {tokens}")
    claims = decode_access_token(tokens["access_token"])
    print(json.dumps({
        key: claims.get(key)
        for key in (
            "iss", "azp", "sub", "preferred_username", "name",
            "associate_id", "store_id", "register_id", "realm_access",
        )
    }, indent=2))

    code_without_verifier, _ = get_authorization_code(
        args.base, args.user, args.password, include_verifier=False
    )
    missing_status, missing_result = exchange_code(args.base, code_without_verifier, None)
    if missing_status < 400 and "error" not in missing_result:
        raise RuntimeError("Code exchange unexpectedly succeeded without a PKCE verifier")
    print(f"PKCE verifier omission rejected: HTTP {missing_status}")

    rejected_status = check_unregistered_redirect(args.base)
    print(f"Unregistered redirect rejected: HTTP {rejected_status}")
    print(f"Discovery document OK: {check_discovery(args.base)}")


if __name__ == "__main__":
    main()
