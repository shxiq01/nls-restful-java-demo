package com.alibaba.nls.client;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUtil {
    private static String getResponseWithTimeout(Request q) {
        String ret = null;
        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
        OkHttpClient client = httpBuilder.connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
        try {
            long start = System.currentTimeMillis();
            Response s = client.newCall(q).execute();
            System.out.println("latency = " + (System.currentTimeMillis() - start) + " ms");
            ret = s.body().string();
            s.close();
        } catch (SocketTimeoutException e) {
            ret = null;
            System.err.println("get result timeout!");
        } catch (IOException e) {
            System.err.println("get result error " + e.getMessage());
        }
        return ret;
    }
    public static String sendPostFile(String url, HashMap<String, String> headers, String fileName) {
        RequestBody body;
        File file = new File(fileName);
        if (!file.isFile()) {
            System.err.println("The filePath is not a file : " + fileName);
            return null;
        } else {
            body = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        }
        Headers.Builder hb = new Headers.Builder();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                hb.add(entry.getKey(), entry.getValue());
            }
        }

        Request request = new Request.Builder()
            .url(url)
            .headers(hb.build())
            .post(body)
            .build();

        String result = getResponseWithTimeout(request);

        return result;
    }

    public static String sendPostData(String url, HashMap<String, String> headers, byte[] data) {
        RequestBody body;
        if (data.length == 0) {
            System.err.println("The send data is empty!");
            return null;
        } else {
            body = RequestBody.create(MediaType.parse("application/octet-stream"), data);
        }
        Headers.Builder hb = new Headers.Builder();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                hb.add(entry.getKey(), entry.getValue());
            }
        }
        Request request = new Request.Builder()
            .url(url)
            .headers(hb.build())
            .post(body)
            .build();
        return getResponseWithTimeout(request);
    }
}