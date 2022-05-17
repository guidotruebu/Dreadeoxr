package com.lody.virtual.helper;

import android.os.Build;

import com.lody.virtual.helper.compat.BuildCompat;
import com.lody.virtual.helper.utils.OSUtils;
import com.lody.virtual.helper.utils.VLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import mirror.dalvik.system.VMRuntime;

/**
 * @author Lody
 */
public class ArtDexOptimizer {
    private static final String TAG = ArtDexOptimizer.class.getSimpleName();

    /**
     * Optimize the dex in interpret mode.
     *
     * @param dexFilePath dex file path
     * @param oatFilePath oat file path
     * @throws IOException
     */
    public static void interpretDex2Oat(String dexFilePath, String oatFilePath) throws IOException {
        // Android 8.x Skip Dex2Oat
        if(BuildCompat.isOreo()){
            VLog.d(TAG,"Android 8+ Skip dex2oat");
            return;
        }

        final File oatFile = new File(oatFilePath);
        if (!oatFile.exists()) {
            oatFile.getParentFile().mkdirs();
        }

        final List<String> commandAndParams = new ArrayList<>();
        commandAndParams.add("dex2oat");
        // for 7.1.1, duplicate class fix
        if (Build.VERSION.SDK_INT >= 24) {
            commandAndParams.add("--runtime-arg");
            commandAndParams.add("-classpath");
            commandAndParams.add("--runtime-arg");
            commandAndParams.add("&");
        }
        commandAndParams.add("--dex-file=" + dexFilePath);
        commandAndParams.add("--oat-file=" + oatFilePath);
        commandAndParams.add("--instruction-set=" + VMRuntime.getCurrentInstructionSet.call());
        commandAndParams.add("--compiler-filter=everything");
        if (Build.VERSION.SDK_INT >= 22 && !OSUtils.getInstance().isAndroidQ()) {
            commandAndParams.add("--compile-pic");
        }
        if (Build.VERSION.SDK_INT > 25) {
            commandAndParams.add("--inline-max-code-units=0");
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                commandAndParams.add("--inline-depth-limit=0");
            }
        }

        final ProcessBuilder pb = new ProcessBuilder(commandAndParams);
        pb.redirectErrorStream(true);
        final Process dex2oatProcess = pb.start();
        StreamConsumer.consumeInputStream(dex2oatProcess.getInputStream());
        StreamConsumer.consumeInputStream(dex2oatProcess.getErrorStream());
        try {
            final int ret = dex2oatProcess.waitFor();
            if (ret != 0) {
                throw new IOException("dex2oat works unsuccessfully, exit code: " + ret);
            }
        } catch (InterruptedException e) {
            throw new IOException("dex2oat is interrupted, msg: " + e.getMessage(), e);
        }
    }

    private static class StreamConsumer {
        static final Executor STREAM_CONSUMER = Executors.newSingleThreadExecutor();

        static void consumeInputStream(final InputStream is) {
            STREAM_CONSUMER.execute(new Runnable() {
                @Override
                public void run() {
                    if (is == null) {
                        return;
                    }
                    final byte[] buffer = new byte[256];
                    try {
                        while ((is.read(buffer)) > 0) {
                            // To satisfy checkstyle rules.
                        }
                    } catch (IOException ignored) {
                        // Ignored.
                    } finally {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                            // Ignored.
                        }
                    }
                }
            });
        }
    }
}
