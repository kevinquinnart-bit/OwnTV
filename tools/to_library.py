"""Turns OpenVPN for Android's app module into a library that KueTV can build in. Run from the ics-openvpn folder."""
import re, sys

def cut_block(s, start):
    """Removes the block starting at the text `start` up to its matching closing brace."""
    i = s.find(start)
    if i < 0:
        sys.exit("not found: " + start)
    j = s.index("{", i)
    depth = 0
    for k in range(j, len(s)):
        if s[k] == "{": depth += 1
        elif s[k] == "}":
            depth -= 1
            if depth == 0:
                return s[:i] + s[k + 1:]
    sys.exit("unbalanced: " + start)

p = "main/build.gradle.kts"
s = open(p).read()
s = s.replace("alias(libs.plugins.android.application)", 'id("com.android.library")', 1)
for line in ["versionCode = 225", 'versionName = "0.7.69"', "targetSdk = 37"]:
    if line not in s: sys.exit("not found: " + line)
    s = s.replace(line, "", 1)
s = s.replace("minSdk = 23", 'minSdk = 23\n        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }', 1)
s = cut_block(s, "androidResources {")
s = cut_block(s, "signingConfigs {")
s = cut_block(s, "splits {")
s = cut_block(s, "bundle {")
# The release build's signing choice: apps only.
s = cut_block(s, 'if (project.hasProperty("icsopenvpnDebugSign"))')
s = cut_block(s, "else {\n                productFlavors")
while "optimization {" in s:
    s = cut_block(s, "optimization {")
s = cut_block(s, "packaging {")
s = re.sub(r'versionNameSuffix = "-o2"', "", s)
open(p, "w").write(s)

r = "build.gradle.kts"
t = open(r).read()
agp = re.search(r'^android-gradle-plugin = "([^"]+)"', open("gradle/libs.versions.toml").read(), re.M).group(1)
t = t.replace("alias(libs.plugins.android.application) apply false",
              "alias(libs.plugins.android.application) apply false\n    id(\"com.android.library\") version \"" + agp + "\" apply false", 1)
open(r, "w").write(t)

# Only the engine is built: not the example app or the certificate helper.
st = "settings.gradle.kts"
u = open(st).read()
u = u.replace('include(":tlsexternalcertprovider")', "").replace('include(":remoteExample")', "")
open(st, "w").write(u)
print("library ready")
