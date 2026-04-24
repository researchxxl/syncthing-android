FROM azul-zulu:21-headless
RUN apt update && apt -y install --no-install-recommends gcc libc6-dev golang-go python3 git ssh unzip
# untrusted public debug.keystore from .github/workflows/common-sign.yaml
COPY debug.keystore /root/.android/
# build script
COPY build.sh /build/
# mount for syncthing-android repo folder
VOLUME /build/app
# build tools cache
VOLUME /build/syncthing-android-prereq
# gradle files cache
VOLUME /root/.gradle
# android cache
VOLUME /root/.android

ENTRYPOINT [ "bash" ]
