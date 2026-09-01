package com.nutomic.syncthingandroid.service.folder;

import android.util.Xml;

import com.nutomic.syncthingandroid.util.FileUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Reads configured folder paths without constructing the normal Dagger graph. */
public final class ConfiguredFolderPathMatcher {

    private ConfiguredFolderPathMatcher() {
    }

    public static boolean contains(File configFile, String requestedPath) throws IOException {
        if (configFile == null || requestedPath == null
                || !new File(requestedPath).isAbsolute()) {
            throw new IOException("Configured folder path must be absolute");
        }
        XmlPullParser parser = Xml.newPullParser();
        try (FileInputStream input = new FileInputStream(configFile)) {
            parser.setInput(input, "UTF-8");
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG || !"folder".equals(parser.getName())) {
                    continue;
                }
                String configuredPath = parser.getAttributeValue(null, "path");
                if (configuredPath != null && pathsEqual(requestedPath, configuredPath)) {
                    return true;
                }
            }
            return false;
        } catch (XmlPullParserException exception) {
            throw new IOException("Unable to parse Syncthing configuration", exception);
        }
    }

    private static boolean pathsEqual(String requestedPath, String configuredPath) {
        if (configuredPath.startsWith("~/")) {
            configuredPath = FileUtils.getSyncthingTildeAbsolutePath()
                    + configuredPath.substring(1);
        }
        try {
            return new File(requestedPath).getCanonicalFile().equals(
                    new File(configuredPath).getCanonicalFile());
        } catch (IOException exception) {
            return requestedPath.equals(configuredPath);
        }
    }
}
