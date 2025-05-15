package com.codelogic.ssdeep;

/* ssdeep
   Copyright (C) 2006 ManTech International Corporation

   $Id: fuzzy.c 97 2010-03-19 15:10:06Z jessekornblum $

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

   The code in this file, and this file only, is based on SpamSum, part
   of the Samba project:
         http://www.samba.org/ftp/unpacked/junkcode/spamsum/

   Because of where this file came from, any program that contains it
   must be licensed under the terms of the General Public License (GPL).
   See the file COPYING for details. The author's original comments
   about licensing are below:



  this is a checksum routine that is specifically designed for spam.
  Copyright Andrew Tridgell <tridge@samba.org> 2002

  This code is released under the GNU General Public License version 2
  or later.  Alternatively, you may also use this code under the terms
  of the Perl Artistic license.

  If you wish to distribute this code under the terms of a different
  free software license then please ask me. If there is a good reason
  then I will probably say yes.

*/

import java.io.*;
import java.util.Arrays;

/**
 * A Java version of the ssdeep algorithm, based on the fuzzy.c source
 * code, taken from version 2.6 of the ssdeep package.
 *
 * Transliteration/port to Java from C by Andrew Jackson &lt;Andrew.Jackson@bl.uk&gt;
 *
 * Additional bug fixes and modifications based off of the 2.13 ssdeep package were added by
 * Russell Francis &lt;russell.francis@gmail.com&gt;
 *
 */
public class SSDeep {
    static private final char[] b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    static private final int HASH_PRIME     = 0x01000193;
    static private final int HASH_INIT      = 0x28021967;
    // Our input buffer when reading files to hash
    static private final int BUFFER_SIZE    = 8192;

    /// Length of an individual fuzzy hash signature component
    static public final int MIN_BLOCKSIZE  = 3;
    static public final int ROLLING_WINDOW = 7;
    static public final int SPAMSUM_LENGTH = 64;

    static private class SSDeepContext {
        final private RollState rollState = new RollState();
        final private char[] p = new char[SPAMSUM_LENGTH];
        final private char[] ret2 = new char[SPAMSUM_LENGTH/2];
        private SSDeepHash ssDeepHash;
        private File file;
        private long totalChars;
        private int h, h2, h3;
        private int j, k;
        private int blockSize;

        private SSDeepContext(final long length) {
            totalChars = length;
            blockSize = MIN_BLOCKSIZE;
            while (((long)blockSize) * SPAMSUM_LENGTH < totalChars) {
                blockSize *= 2;
            }
            reset();
        }

        private SSDeepContext(final byte[] buf) {
            this(buf.length);
        }

        private SSDeepContext(final File file) {
            this(file.length());
            this.file = file;
        }

        public void digest(final byte[] buffer) {
            digest(buffer, buffer.length);
        }

        public void digest(final byte[] buffer, final int bufferSize) {
            for ( int i = 0 ; i < bufferSize ; ++i)
            {
	            // at each character we update the rolling hash and
	            // the normal hash. When the rolling hash hits the
	            // reset value then we emit the normal hash as a
	            // element of the signature and reset both hashes
                rollState.rollHash(buffer[i]);// & 0x7FFFFFFF;
                h = rollState.rollSum();
                h2 = sumHash(buffer[i], h2);// & 0x7FFFFFFF;
                h3 = sumHash(buffer[i], h3);// & 0x7FFFFFFF;

                if (((0xFFFFFFFFL & h) % blockSize) == (blockSize - 1)) {
                    // we have hit a reset point. We now emit a
                    // hash which is based on all characters in the
                    // piece of the message between the last reset
                    // point and this one.
                    p[j] = b64[(h2 & 0xFFFF) % b64.length];

                    if (j < SPAMSUM_LENGTH - 1) {
		                // we can have a problem with the tail
		                // overflowing. The easiest way to
		                // cope with this is to only reset the
		                // second hash if we have room for
		                // more characters in our
                        // signature. This has the effect of
		                // combining the last few pieces of
		                // the message into a single piece.
                        h2 = HASH_INIT;
                        j++;
                    }
                }

                // this produces a second signature with a block size
	            // of blockSize*2. By producing dual signatures in
	            // this way the effect of small changes in the message
	            // size near a block size boundary is greatly reduced.
                if (((0xFFFFFFFFL & h) % (blockSize * 2)) == ((blockSize * 2) - 1)) {
                    ret2[k] = b64[h3 & 0xFFFF % 64];
                    if (k < ((SPAMSUM_LENGTH / 2) - 1)) {
                        h3 = HASH_INIT;
                        k++;
                    }
                }
            }
        }

        public void reset() {
            j = 0;
            k = 0;
            h  = 0;
            h2 = HASH_INIT;
            h3 = HASH_INIT;
            rollState.reset();
            Arrays.fill(p, (char)0);
            Arrays.fill(ret2, (char)0 );
        }

        /* a simple non-rolling hash, based on the FNV hash */
        private int sumHash(final int c, final int h)
        {
            //h = h & 0xFFFFFFFF
            return (h * HASH_PRIME) ^ c;
        }

        public boolean isFinished() {
            // our blocksize guess may have been way off - repeat if necessary
            if (blockSize > MIN_BLOCKSIZE && j < SPAMSUM_LENGTH / 2) {
                blockSize /= 2;
                reset();
                return false;
            }
            return true;
        }

        public SSDeepHash generateHash() {
            if (ssDeepHash == null) {
                if (h != 0) {
                    p[j] = b64[(h2 & 0xFFFF) % b64.length];
                    ret2[k] = b64[(h3 & 0xFFFF) % b64.length];
                }
                ssDeepHash = file == null ?
                        new SSDeepHash(blockSize, String.valueOf(p), String.valueOf(ret2)) :
                        new SSDeepHash(blockSize, String.valueOf(p), String.valueOf(ret2), file.getPath());
            }
            return ssDeepHash;
        }
    }

    /**
     * Generate an SSDeepHash instance from a string.
     *
     * @param ssdeepHash The SSDeep hash string.
     * @return A new SSDeep hash instance generated from that string.
     */
    public SSDeepHash fromString(String ssdeepHash) {
        return new SSDeepHash(ssdeepHash);
    }

    /**
     * Generate an SSDeep hash from the given input stream.  The current implementation reads the entire stream into
     * a byte[] before hashing and is not suitable for hashing very larg inputs.
     *
     * @param ins The input stream to hash.
     * @return The SSDeepHash value for this input stream.
     * @throws IOException If there is an error reading from the stream.
     */
    public SSDeepHash generateHash(final InputStream ins) throws IOException {
        ByteArrayOutputStream outs = new ByteArrayOutputStream();
        int bytesRead;
        final byte[] buf = new byte[BUFFER_SIZE];
        while ( (bytesRead = ins.read(buf)) > 0) {
            outs.write(buf, 0, bytesRead);
        }
        return generateHash(outs.toByteArray());
    }

    /**
     * Generate an SSDeep hash from the given file.
     *
     * @param handle The file to hash.
     * @return An SSDeep hash for the file.
     * @throws IOException If there is an error reading from the file.
     */
    public SSDeepHash generateHash(final File handle) throws IOException
    {
        int bytesRead;
        final byte[] buf = new byte[BUFFER_SIZE];
        final SSDeepContext ctx = new SSDeepContext(handle);

        do
        {
            try (FileInputStream ins = new FileInputStream(handle)) {
                while ( (bytesRead = ins.read(buf)) > 0) {
                    ctx.digest(buf, bytesRead);
                }
            }
        } while (!ctx.isFinished());

        return ctx.generateHash();
    }

    /**
     * Generate an SSDeep hash from the given byte array.
     *
     * @param buf The byte array to hash.
     * @return The SSDeep hash of the byte array.
     */
    public SSDeepHash generateHash(final byte[] buf) {
        final SSDeepContext ctx = new SSDeepContext(buf);
        do {
            ctx.digest(buf);
        } while (!ctx.isFinished());

        return ctx.generateHash();
    }
}
