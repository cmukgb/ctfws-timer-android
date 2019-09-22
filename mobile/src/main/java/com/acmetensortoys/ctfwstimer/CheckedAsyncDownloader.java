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

    public enum Result {
        RES_OK,
        RES_UNTRIED,     /* Not yet tried */
        RES_ALREADY,     /* Existing file matches checksum */
        ERR_WRITE,       /* Local FS error */
        ERR_HOSTUNREACH, /* Could not establish connection */
        ERR_XFER,        /* Error during transfer */
        ERR_CHECKSUM,    /* Checksum did not match after xfer */
        ERR_TOO_LONG,    /* File longer than maximum permitted */
    }

    public static class DL {
        final URL url;
        final byte[] sha256;
        final File dest;
        final long lengthLimit; /* In bytes, or 0 for no limit */
        Result result;
        long dlsize;

        public DL(URL url, byte[] sha256, long lim, File dest) {
            this.url = url;
            this.sha256 = sha256;
            this.dest = dest;
            this.lengthLimit = lim;
            this.result = Result.RES_UNTRIED;
            this.dlsize = 0;
        }

        public DL(DL dl) {
            this.url = dl.url;
            this.sha256 = dl.sha256;
            this.dest = dl.dest;
            this.lengthLimit = dl.lengthLimit;
            this.result = dl.result;
            this.dlsize = dl.dlsize;
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
                    dl.result = Result.RES_ALREADY;
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
                dl.result = Result.ERR_WRITE;
                continue;
            }

            try {
                os = new FileOutputStream(oft);
            } catch (IOException ioe) {
                dl.result = Result.ERR_WRITE;
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
                dl.result = Result.ERR_HOSTUNREACH;
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
                        dl.result = Result.ERR_TOO_LONG;
                        oft.delete();
                        continue dlfor;
                    }
                }

                is.close();
                os.close();
            } catch (IOException ioe) {
                dl.result = Result.ERR_XFER;
                oft.delete();
                continue;
            }

            if (!java.util.Arrays.equals(is.getMessageDigest().digest(), dl.sha256)) {
                dl.result = Result.ERR_CHECKSUM;
                oft.delete();
                continue;
            }

            if (!oft.renameTo(dl.dest)) {
                dl.result = Result.ERR_WRITE;
                oft.delete();
                continue;
            }

            dl.result = Result.RES_OK;
            dl.dlsize = xfer;
        }

        return null;
    }
}