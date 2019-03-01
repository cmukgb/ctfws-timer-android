package com.acmetensortoys.ctfwstimer;

import android.content.Context;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.NoSuchFileException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CheckedAsyncDownloader extends AsyncTask<CheckedAsyncDownloader.DL, Void, Void> {

    public static final long ERR_UNTRIED     = -1; /* Not yet tried */
    public static final long ERR_ALREADY     = -2; /* Existing file matches checksum */
    public static final long ERR_WRITE       = -3; /* Local FS error */
    public static final long ERR_HOSTUNREACH = -4; /* Could not establish connection */
    public static final long ERR_XFER        = -5; /* Error during transfer */
    public static final long ERR_CHECKSUM    = -6; /* Checksum did not match after xfer */
    public static final long ERR_TOO_LONG    = -7; /* File longer than maximum permitted */

    public static class DL {
        final URL url;
        final byte[] sha256;
        final File dest;
        final long lengthLimit; /* In bytes, or 0 for no limit */
        long result;

        public DL(URL url, byte[] sha256, long lim, File dest) {
            this.url = url;
            this.sha256 = sha256;
            this.dest = dest;
            this.lengthLimit = lim;
            this.result = ERR_UNTRIED;
        }
    }

    @Override
    protected Void doInBackground(DL... dls) {
        MessageDigest md;

        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException nsae) {
            return null;
        }

        dlfor: for (DL dl : dls) {
            try {
                DigestInputStream is = new DigestInputStream(
                        new BufferedInputStream(new FileInputStream(dl.dest)),
                        md);

                byte[] data = new byte[4096];

                while (is.read(data) != -1) { ; }

                if (java.util.Arrays.equals(is.getMessageDigest().digest(), dl.sha256)) {
                    dl.result = ERR_ALREADY;
                    continue;
                }

            } catch (FileNotFoundException nsfe) {
                /* OK, the file isn't there, just keep going */
                ;
            } catch (IOException ioe) {
                /*
                 * Something has gone really wrong.
                 * Unlink the file and try to fetch it
                 */
                dl.dest.delete();
            }

            DigestInputStream is;
            File oft;
            OutputStream os;
            long xfer = 0;

            try {
                oft = File.createTempFile(dl.dest.getName(), "dl");
            } catch (IOException ioe) {
                dl.result = ERR_WRITE;
                continue;
            }

            try {
                os = new FileOutputStream(oft);
            } catch (IOException ioe) {
                dl.result = ERR_WRITE;
                oft.delete();
                continue;
            }

            try {
                URLConnection urlc = dl.url.openConnection();

                is = new DigestInputStream(
                        new BufferedInputStream(urlc.getInputStream()),
                        md
                    );
            } catch (IOException ioe) {
                dl.result = ERR_HOSTUNREACH;
                oft.delete();
                continue;
            }

            try {
                byte[] data = new byte[4096];
                int count;

                while ((count = is.read(data)) != -1) {
                    xfer += count;
                    os.write(data, 0, count);

                    if (dl.lengthLimit > 0 && xfer > dl.lengthLimit) {
                        is.close();
                        os.close();
                        dl.result = ERR_TOO_LONG;
                        oft.delete();
                        continue dlfor;
                    }
                }

                is.close();
                os.close();
            } catch (IOException ioe) {
                dl.result = ERR_XFER;
                oft.delete();
                continue;
            }

            if (!java.util.Arrays.equals(is.getMessageDigest().digest(), dl.sha256)) {
                dl.result = ERR_CHECKSUM;
                oft.delete();
                continue;
            }

            if (!oft.renameTo(dl.dest)) {
                dl.result = ERR_WRITE;
                oft.delete();
                continue;
            }

            dl.result = xfer;
        }

        return null;
    }
}