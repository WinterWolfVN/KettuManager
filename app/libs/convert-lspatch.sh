# Download LSPatch jar and convert it to an aar
# Jars cannot be used as a dependency without breaking R8 optimization

NPATCH_URL="https://github.com/7723mod/NPatch/releases/download/v0.8.0/jar-v0.8.0-538-release.jar"
NPATCH_FILE_NAME="npatch"

function cleanup {
  rm -rf $NPATCH_FILE_NAME.jar META-INF AndroidManifest.xml classes.jar R.txt
}

cleanup

curl -sSL -o $NPATCH_FILE_NAME.jar $NPATCH_URL

unzip -q $NPATCH_FILE_NAME.jar "META-INF/**"
mv $NPATCH_FILE_NAME.jar classes.jar
touch R.txt
cat >AndroidManifest.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.lsposed.patch.npatch">
    <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34" />
</manifest>
EOF

rm -f $NPATCH_FILE_NAME.aar
zip -rq $NPATCH_FILE_NAME.aar R.txt AndroidManifest.xml META-INF classes.jar
cleanup
