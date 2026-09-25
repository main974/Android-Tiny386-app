#!/data/data/com.termux/files/usr/bin/bash
set -e
A="$HOME/t386app"; STUB="$HOME/android-all.jar"
cd "$A"
find gen out -mindepth 0 -delete 2>/dev/null || true
# ---- 自动版本号 ----
BUILDNUM=$(cat "$A/.buildnum" 2>/dev/null || echo 13)
BUILDNUM=$((BUILDNUM+1))
echo $BUILDNUM > "$A/.buildnum"
VERSIONNAME="0.0.1-b$BUILDNUM"
sed -e "s/@VERSIONCODE@/$BUILDNUM/" -e "s/@VERSIONNAME@/$VERSIONNAME/" \
    "$A/AndroidManifest.xml.in" > "$A/AndroidManifest.xml"
echo "[0/5] 版本: $VERSIONNAME (versionCode=$BUILDNUM)"

# 原生库不存在（或传了 full）时就编译，参数与上游对齐
if [ ! -f libtiny386.so ] || [ "$1" = "full" ]; then
  echo "  [native] 编译 libtiny386.so ..."
  clang -O3 -w -DNO_ALOG -fPIC -shared -I jni \
    -DI386_ENABLE_FPU -DI386_ENABLE_MMX -DI386_ENABLE_SSE \
    -DI386_ENABLE_SSE2 -DI386_ENABLE_SSE3 -DI386_ENABLE_SSSE3 \
    -ffunction-sections -fdata-sections \
    -o libtiny386.so jni/tiny386/*.c bridge.c -lm -Wl,--gc-sections
fi
mkdir -p gen out/classes

echo "[1/5] aapt：资源 + assets + R.java"
aapt package -f -m -J gen -M AndroidManifest.xml -S res -A assets -I "$STUB" -F out/app.unaligned.apk

echo "[2/5] javac"
javac -source 8 -target 8 -nowarn -cp "$STUB" -d out/classes $(find src gen -name "*.java")

echo "[3/5] d8：转 dex"
d8 --min-api 21 --lib "$STUB" --output out $(find out/classes -name "*.class")

echo "[4/5] 塞 dex + 原生库"
( cd out && aapt add app.unaligned.apk classes.dex >/dev/null )
mkdir -p out/lib/arm64-v8a
cp libtiny386.so out/lib/arm64-v8a/
( cd out && aapt add app.unaligned.apk lib/arm64-v8a/libtiny386.so >/dev/null )

echo "[5/5] 签名"
if [ ! -f "$HOME/debug.keystore" ]; then
  keytool -genkeypair -keystore "$HOME/debug.keystore" -alias a \
    -storepass android -keypass android -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Debug,O=Android,C=CN" >/dev/null 2>&1
fi
apksigner sign --ks "$HOME/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --out out/t386.apk out/app.unaligned.apk

echo
echo "✅ 完成 → $A/out/t386.apk"
ls -lh out/t386.apk

# COPY_VERIFY: 拷贝到下载目录并校验 md5，不一致就报警
DEST=/sdcard/Download/tiny386.apk
cp out/t386.apk "$DEST" && sync
M1=$(md5sum out/t386.apk | cut -d' ' -f1)
M2=$(md5sum "$DEST" | cut -d' ' -f1)
if [ "$M1" = "$M2" ]; then
  echo "✅ 已拷贝到下载目录（md5 校验通过）"
else
  echo "❌ 拷贝校验失败！$M1 != $M2 —— 请重新 cp"
fi
