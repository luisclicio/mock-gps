FROM eclipse-temurin:17.0.19_10-jdk-noble

ARG ANDROID_CMDLINE_TOOLS_REV=15859902
ARG ANDROID_PLATFORM=36
ARG ANDROID_BUILD_TOOLS=35.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip bash \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /gradle-cache \
    && chmod 777 /gradle-cache \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REV}_latest.zip" -o /tmp/android-command-line-tools.zip \
    && unzip -q /tmp/android-command-line-tools.zip -d /tmp/android-command-line-tools \
    && mv /tmp/android-command-line-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" \
    && rm -rf /tmp/android-command-line-tools /tmp/android-command-line-tools.zip \
    && yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null \
    && sdkmanager --sdk_root="$ANDROID_HOME" \
        "platforms;android-${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
    && chmod -R a+rX "$ANDROID_HOME"

WORKDIR /workspace
CMD ["bash"]
