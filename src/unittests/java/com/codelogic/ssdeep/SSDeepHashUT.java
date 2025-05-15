package com.codelogic.ssdeep;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SSDeepHashUT {

    @Test
    public void testSSDeepConstructor_WithFilename() {
        final SSDeep ssDeep = new SSDeep();
        final String HASH_STRING = "3072:ttv0RWwkblumieymMvVZssQb+2jfrxM5HbVGiqmLGSfAOwiSDC4nMgFW7c5e:tXbYwM4sQb+HsajYOwDRnMVc8,\"/unittests/resources/pg4280.txt\"";
        final SSDeepHash hash = ssDeep.fromString(HASH_STRING);
        assertEquals("/unittests/resources/pg4280.txt", hash.getFilename());
        assertEquals(3072, hash.getBlocksize());
        assertEquals("ttv0RWwkblumieymMvVZssQb+2jfrxM5HbVGiqmLGSfAOwiSDC4nMgFW7c5e", hash.getHashString());
        assertEquals("tXbYwM4sQb+HsajYOwDRnMVc8", hash.getHash2String());
        assertEquals(HASH_STRING, hash.toString());
    }

    @Test
    public void testSSDeepConstructor_WithoutFilename() {
        final SSDeep ssDeep = new SSDeep();
        final String HASH_STRING = "3072:ttv0RWwkblumieymMvVZssQb+2jfrxM5HbVGiqmLGSfAOwiSDC4nMgFW7c5e:tXbYwM4sQb+HsajYOwDRnMVc8";
        final SSDeepHash hash = ssDeep.fromString(HASH_STRING);
        assertNull(hash.getFilename());
        assertEquals(3072, hash.getBlocksize());
        assertEquals("ttv0RWwkblumieymMvVZssQb+2jfrxM5HbVGiqmLGSfAOwiSDC4nMgFW7c5e", hash.getHashString());
        assertEquals("tXbYwM4sQb+HsajYOwDRnMVc8", hash.getHash2String());
        assertEquals(HASH_STRING, hash.toString());
    }

    @Test
    public void testSSDeep_Compare_Exact() {
        final SSDeep ssDeep = new SSDeep();
        final String HASH_STRING = "3072:ttv0RWwkblumieymMvVZssQb+2jfrxM5HbVGiqmLGSfAOwiSDC4nMgFW7c5e:tXbYwM4sQb+HsajYOwDRnMVc8";
        final SSDeepHash a = ssDeep.fromString(HASH_STRING);
        final SSDeepHash b = ssDeep.fromString(HASH_STRING);
        assertEquals(100, a.compare(b));
        assertEquals(100, b.compare(a));
    }

    @Test
    public void testSSDeep_Compare_Uncomparable() {
        final SSDeep ssDeep = new SSDeep();
        final String HASH_A = "3:Aa:X,\"/tmp/does/not/exist.tmp\"";
        final String HASH_B = "3072:ttv0RWwkblumieymMvVZssQb+2jfrxM5HbVGiqmLGSfAOwiSDC4nMgFW7c5e:tXbYwM4sQb+HsajYOwDRnMVc8";
        final SSDeepHash a = ssDeep.fromString(HASH_A);
        final SSDeepHash b = ssDeep.fromString(HASH_B);
        assertEquals(0, a.compare(b));
        assertEquals(0, b.compare(a));
    }

    @Test
    public void testSSDeep_Compare_NotExact() {
        final SSDeep ssDeep = new SSDeep();
        final String HASH_A = "12:E+DNeM7FIBMcABQN4OJ4FuWFRnIWlB7226CC+IiZABQNW1GoQI+PsNxVezkh8R0l:xoLhNL6uWDn72jz6ZhNICAZkkmYn,\"/tmp/example/a.asc\"";
        final String HASH_B = "12:E+DNeM7FIBMcABQN4OJ4FuWFRnIWlB7226CC+IiZABQNW1GoQI+PsNxVezkh8R0j:xoLhNL6uWDn72jz6ZhNICAZkkmYp,\"/tmp/example/b.asc\"";
        final SSDeepHash a = ssDeep.fromString(HASH_A);
        final SSDeepHash b = ssDeep.fromString(HASH_B);
        assertEquals(99, a.compare(b));
        assertEquals(99, b.compare(a));
    }

    @Test
    public void testLongRuns() {
        final SSDeep ssDeep = new SSDeep();
        final SSDeepHash a = ssDeep.fromString("25165824:AxtRk99Tq9xFom9uKDfodaUyJnrAvV32xEFVh+vB:8k9tuxFXwaUirimaFVh+vB,\"stdin\"");
        final SSDeepHash b = ssDeep.fromString("25165824:AxtRk99Tq9xFom9uKDfodaUyJnrAvV32xEFVh+vUrD:8k9tuxFXwaUirimaFVh+vU3,\"stdin\"");
        assertEquals(96, a.compare(b));
        assertEquals(96, b.compare(a));
    }

    @Test
    public void testAnotherCase() {
        final SSDeep ssDeep = new SSDeep();
        final SSDeepHash a = ssDeep.fromString("3072:n04wOt1HIpUPUiEv6f2M2NGnd1kOSZ7Co7ifCu:nfwEHZPfuMhdaf717iKu,\"stdin\"");
        final SSDeepHash b = ssDeep.fromString("3072:n04wOt1HIpUcdiEv6f2M2NGnd1kOSb7Co7ifCa:afwEaZPfuMhdaf717iKa,\"stdin\"");
        assertEquals(91, a.compare(b));
        assertEquals(91, b.compare(a));
    }

    @Test
    public void testSmallBlockSize() {
        final SSDeep ssDeep = new SSDeep();
        final SSDeepHash a = ssDeep.fromString("3:sMDM:sGM,\"stdin\"");
        final SSDeepHash b = ssDeep.fromString("3:sMDM:sGM,\"stdin\"");
        assertEquals(100, a.compare(b));
        assertEquals(100, b.compare(a));
    }

    @Test
    public void testSmallBlockSize_NotEqual() {
        final SSDeep ssDeep = new SSDeep();
        final SSDeepHash a = ssDeep.fromString("3:sDsET:sDsw,\"stdin\"");
        final SSDeepHash b = ssDeep.fromString("3:sMDM:sGM,\"stdin\"");
        assertEquals(0, a.compare(b));
        assertEquals(0, b.compare(a));
    }
}
