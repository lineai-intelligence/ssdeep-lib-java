package com.codelogic.ssdeep;

import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;

public class SSDeepTestUT {

    @Test
    public void testSSDeepHashGeneration_Example1() throws IOException {
        final String EXPECTED_HASH = "24576:xDVgEEuF2aZGNzfOyFQeyHvclPsMVxWnt/JC4wNzLgsI:nAuF/GNDqH0lPsMVxu/JCfZMsI";
        SSDeep ssDeep = new SSDeep();
        SSDeepHash a;
        try (InputStream ins = getClass().getResourceAsStream("/examples/example1.img")) {
            a = ssDeep.generateHash(ins);
        }
        assertEquals(EXPECTED_HASH, a.toString());
    }

    @Test
    public void testSSDeepHashGeneration_Example1_similar() throws IOException {
        final String EXPECTED_HASH = "24576:cDVgEEuF2aZGNzfOyFQeyHvclPsMVxWnt/JC4wNzLgs8:sAuF/GNDqH0lPsMVxu/JCfZMs8";
        SSDeep ssDeep = new SSDeep();
        SSDeepHash a;
        try (InputStream ins = getClass().getResourceAsStream("/examples/example1_similar.img")) {
            a = ssDeep.generateHash(ins);
        }
        assertEquals(EXPECTED_HASH, a.toString());
    }

    @Test
    public void testSSDeepHashGeneration_Example2() throws IOException {
        final String EXPECTED_HASH = "192:26pWVDzxMgIfrsF2HNN8CZVcUu3cYon8I3NCvIf+HAC4Fn1b/kNed3R:eHWgYHf/4Qn8I3NCvcaen1woh";
        SSDeep ssDeep = new SSDeep();
        SSDeepHash a;
        try (InputStream ins = getClass().getResourceAsStream("/examples/example2.img")) {
            a = ssDeep.generateHash(ins);
        }
        assertEquals(EXPECTED_HASH, a.toString());
    }

    @Test
    public void testSSDeepHashGenerationOf_4280() throws IOException {
        final String EXPECTED_HASH = "3072:ttv0RWwkblumieymMvVZssQb+2jfrxM5HbVGiqmLGSfAOwiSDC4nMgFW7c5e:tXbYwM4sQb+HsajYOwDRnMVc8";
        SSDeep ssDeep = new SSDeep();
        SSDeepHash a;
        try (InputStream ins = getClass().getResourceAsStream("/pg4280.txt")) {
            a = ssDeep.generateHash(ins);
        }
        assertEquals(EXPECTED_HASH, a.toString());
    }

    @Test
    public void testSSDeepHashGeneration() throws IOException {
        final String EXPECTED_A_HASH = "12:E+DNeM7FIBMcABQN4OJ4FuWFRnIWlB7226CC+IiZABQNW1GoQI+PsNxVezkh8R0l:xoLhNL6uWDn72jz6ZhNICAZkkmYn";
        final String EXPECTED_B_HASH = "12:E+DNeM7FIBMcABQN4OJ4FuWFRnIWlB7226CC+IiZABQNW1GoQI+PsNxVezkh8R0j:xoLhNL6uWDn72jz6ZhNICAZkkmYp";
        SSDeep ssDeep = new SSDeep();
        SSDeepHash a, b;
        
        try (InputStream ins = getClass().getResourceAsStream("/book_text.txt")) {
            a = ssDeep.generateHash(ins);
        }
        
        try (InputStream ins = getClass().getResourceAsStream("/book_text_similar.txt")) {
            b = ssDeep.generateHash(ins);
        }

        assertEquals(EXPECTED_A_HASH, a.toString());
        assertEquals(EXPECTED_B_HASH, b.toString());
        assertEquals(99, a.compare(b));
    }

    @Test
    public void testSSDeepBinaryHashGeneration() throws IOException {
        final String EXPECTED_A_HASH = "24576:xDVgEEuF2aZGNzfOyFQeyHvclPsMVxWnt/JC4wNzLgsI:nAuF/GNDqH0lPsMVxu/JCfZMsI";
        final String EXPECTED_B_HASH = "24576:cDVgEEuF2aZGNzfOyFQeyHvclPsMVxWnt/JC4wNzLgs8:sAuF/GNDqH0lPsMVxu/JCfZMs8";
        SSDeep ssDeep = new SSDeep();
        SSDeepHash a, b;
        
        try (InputStream ins = getClass().getResourceAsStream("/examples/example1.img")) {
            a = ssDeep.generateHash(ins);
        }
        
        try (InputStream ins = getClass().getResourceAsStream("/examples/example1_similar.img")) {
            b = ssDeep.generateHash(ins);
        }

        assertEquals(EXPECTED_A_HASH, a.toString());
        assertEquals(EXPECTED_B_HASH, b.toString());
        assertEquals(97, a.compare(b));
    }

    @Test
    public void testSSDeepHashGenerationInParallel() throws IOException, ExecutionException, InterruptedException {
        final String EXPECTED_A_HASH = "12:E+DNeM7FIBMcABQN4OJ4FuWFRnIWlB7226CC+IiZABQNW1GoQI+PsNxVezkh8R0l:xoLhNL6uWDn72jz6ZhNICAZkkmYn";

        byte[] buf = new byte[4096];
        try (InputStream ins = getClass().getResourceAsStream("/book_text.txt")) {
            ByteArrayOutputStream outs = new ByteArrayOutputStream();
            try {
                int bytesRead;
                while ((bytesRead = ins.read(buf)) > 0) {
                    outs.write(buf, 0, bytesRead);
                }
            }
            finally {
                outs.close();
            }
            buf = outs.toByteArray();
        }
        final byte[] buffer = buf;
        final SSDeep ssDeep = new SSDeep();
        final List<Future<SSDeepHash>> futureList = new ArrayList<>();

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10000; ++i) {
            futureList.add(executorService.submit(() -> ssDeep.generateHash(buffer)));
        }
        for (Future<SSDeepHash> future : futureList) {
            assertEquals(EXPECTED_A_HASH, future.get().toString());
        }
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
    }
}
