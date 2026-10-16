#!/usr/bin/env bash
set -euo pipefail
# Linux x86_64: JDK 17, CMake 3.31, SWIG 4.5, Ninja, make, C/C++, autoconf,
# automake, autoconf-archive, libtool, pkg-config, Python 3 and Android NDK r27b.
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK r27b}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
project_dir=$(cd "$(dirname "$0")/.." && pwd)
build_dir=${MEGA_BUILD_DIR:-"$project_dir/.native-build"}
mkdir -p "$build_dir"
cd "$project_dir/native"
sha256sum -c SHA256SUMS
if [ ! -d "$build_dir/sdk-10.19.0" ]; then
  tar -xzf mega-sdk-v10.19.0.tar.gz -C "$build_dir"
  patch -d "$build_dir/sdk-10.19.0" -p1 < binary-streaming-swig.patch
fi
if [ ! -d "$build_dir/vcpkg/.git" ]; then
  git clone https://github.com/microsoft/vcpkg.git "$build_dir/vcpkg"
  git -C "$build_dir/vcpkg" checkout ef7dbf94b9198bc58f45951adcf1f041fcbc5ea0
  "$build_dir/vcpkg/bootstrap-vcpkg.sh" -disableMetrics
fi
export VCPKG_ROOT="$build_dir/vcpkg"
export VCPKG_MAX_CONCURRENCY=${VCPKG_MAX_CONCURRENCY:-4}
cd "$build_dir"
cmake -S sdk-10.19.0 -B android-arm64 --preset mega-android \
  -DVCPKG_ROOT="$VCPKG_ROOT" -DVCPKG_TARGET_TRIPLET=arm64-nube \
  -DVCPKG_OVERLAY_TRIPLETS="$project_dir/native" \
  -DCMAKE_BUILD_TYPE=Release -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=28 \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DENABLE_SYNC=ON -DENABLE_CHAT=OFF \
  -DUSE_LIBUV=OFF -DENABLE_MEDIA_FILE_METADATA=OFF \
  -DENABLE_SDKLIB_TESTS=OFF -DENABLE_SDKLIB_EXAMPLES=OFF
cmake --build android-arm64 -j "${VCPKG_MAX_CONCURRENCY}"
mkdir -p "$project_dir/app/src/main/jniLibs/arm64-v8a"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded \
  android-arm64/bindings/java/libmega.so \
  -o "$project_dir/app/src/main/jniLibs/arm64-v8a/libmega.so"
cp android-arm64/bindings/java/nz/mega/sdk/*.java "$project_dir/app/src/main/java/nz/mega/sdk/"
printf 'Native library and generated Java bindings updated. Rebuild the APK with Gradle.\n'
