import socket
import urllib.request
import urllib.error
import sys
import os

def check_internet_connection(host="8.8.8.8", port=53, timeout=3):
    """
    Check if internet is available by connecting to Google DNS.
    """
    try:
        socket.setdefaulttimeout(timeout)
        socket.socket(socket.AF_INET, socket.SOCK_STREAM).connect((host, port))
        print(f"[PASS] Internet Connection ({host}:{port})")
        return True
    except socket.error as ex:
        print(f"[FAIL] Internet Connection: {ex}")
        return False

def check_local_server(url="http://127.0.0.1:8000/health"):
    """
    Check if the local API server is responding.
    """
    try:
        with urllib.request.urlopen(url, timeout=2) as response:
            if response.status == 200:
                print(f"[PASS] Local API Server ({url})")
                return True
            else:
                print(f"[FAIL] Local API Server returned {response.status}")
                return False
    except urllib.error.URLError as e:
        print(f"[FAIL] Local API Server is NOT running or not reachable at {url}. Error: {e}")
        return False
    except Exception as e:
        print(f"[FAIL] Local API Server error: {e}")
        return False

def check_dns_resolution(domain="google.com"):

    """
    Check if DNS resolution is working.
    """
    try:
        ip = socket.gethostbyname(domain)
        print(f"[PASS] DNS Resolution ({domain} -> {ip})")
        return True
    except socket.error as e:
        print(f"[FAIL] DNS Resolution for {domain}: {e}")
        return False

if __name__ == "__main__":
    print("=== Network Diagnostics Tool ===")
    
    internet = check_internet_connection()
    dns = check_dns_resolution()
    server = check_local_server()
    
    print("\n=== Summary ===")
    if internet and dns and server:
        print("✅ All network checks passed.")
        sys.exit(0)
    else:
        print("❌ Some network checks failed.")
        if not internet:
            print("  - Check your Wi-Fi or Ethernet cable.")
        if not dns:
            print("  - Check your DNS settings.")
        if not server:
            print("  - Ensure the backend server is running (uvicorn app.main:app).")
        sys.exit(1)
