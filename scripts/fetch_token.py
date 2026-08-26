#!/usr/bin/env python3
"""
LyricsStatus Token Fetcher Script
---------------------------------
Validates a Discord user token and fetches the Spotify connection access token.
"""

import sys
import json
import urllib.request
import urllib.error
import ssl

DISCORD_ME_URL = "https://discord.com/api/v10/users/@me"
DISCORD_CONNECTIONS_URL = "https://discord.com/api/v10/users/@me/connections"

def fetch_discord_info(token: str):
    token = token.strip().replace('"', '')
    if not token:
        print("[-] Error: Token cannot be empty.")
        return None

    ctx = ssl.create_default_context()
    headers = {
        "Authorization": token,
        "User-Agent": "Mozilla/5.0 (compatible; LyricsStatus/0.1)"
    }

    # 1. Fetch User Profile
    req = urllib.request.Request(DISCORD_ME_URL, headers=headers)
    try:
        with urllib.request.urlopen(req, context=ctx) as res:
            user = json.loads(res.read().decode())
            print("\n[+] Discord User Verified Successfully:")
            print(f"    - Username:    {user.get('username')}")
            print(f"    - Global Name: {user.get('global_name')}")
            print(f"    - User ID:     {user.get('id')}")
    except urllib.error.HTTPError as e:
        print(f"[-] Discord API HTTP Error: {e.code} - {e.reason}")
        return None
    except Exception as e:
        print(f"[-] Error connecting to Discord: {e}")
        return None

    # 2. Fetch Connections (Spotify)
    req_conn = urllib.request.Request(DISCORD_CONNECTIONS_URL, headers=headers)
    try:
        with urllib.request.urlopen(req_conn, context=ctx) as res:
            connections = json.loads(res.read().decode())
            spotify = next((c for c in connections if c.get("type") == "spotify"), None)
            if spotify:
                print("\n[+] Connected Spotify Account:")
                print(f"    - Account Name: {spotify.get('name')}")
                print(f"    - Spotify ID:   {spotify.get('id')}")
                access_token = spotify.get("access_token")
                if access_token:
                    print(f"    - Access Token: {access_token[:10]}... (active)")
                else:
                    print("    - Access Token: (none or expired)")
            else:
                print("\n[-] No Spotify account connected in Discord user settings.")
    except Exception as e:
        print(f"[-] Error fetching connections: {e}")

    print("\n[+] Done! You can now paste this token into LyricsStatus Settings.\n")
    return True

if __name__ == "__main__":
    if len(sys.argv) > 1:
        token = sys.argv[1]
    else:
        print("LyricsStatus Token Fetcher CLI")
        print("------------------------------")
        token = input("Enter your Discord User Token: ")

    fetch_discord_info(token)
