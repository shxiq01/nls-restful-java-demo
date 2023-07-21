package com.alibaba.nls.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;

import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 此示例演示了
 *      1. TTS的RESTFul接口调用
 *      2. 启用http chunked机制的处理方式(流式返回)
 */
public class SpeechSynthesizerRestfulChunkedDemo {
    private static Logger logger = LoggerFactory.getLogger(SpeechSynthesizerRestfulChunkedDemo.class);
    private String accessToken;
    private String appkey;
    public SpeechSynthesizerRestfulChunkedDemo(String appkey, String token) {
        this.appkey = appkey;
        this.accessToken = token;
    }

    public void processGETRequet(String text, final String audioSaveFile, String format, int sampleRate, String voice, boolean chunked) {
        /**
         * 设置HTTPS GET请求
         * 1.使用HTTPS协议
         * 2.语音识别服务域名：nls-gateway.cn-shanghai.aliyuncs.com
         * 3.语音识别接口请求路径：/stream/v1/tts
         * 4.设置必须请求参数：appkey、token、text、format、sample_rate
         * 5.设置可选请求参数：voice、volume、speech_rate、pitch_rate
         * 6.设置参数chunk，启用http流式返回
         */
        String url = "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/tts";
        url = url + "?appkey=" + appkey;
        url = url + "&token=" + accessToken;
        url = url + "&text=" + text;
        url = url + "&format=" + format;
        url = url + "&voice=" + voice;
        url = url + "&sample_rate=" + String.valueOf(sampleRate);
        url = url + "&chunk=" + String.valueOf(chunked);
        System.out.println("URL: " + url);

        try {
            AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(3000)
                .setKeepAlive(true)
                .setReadTimeout(10000)
                .setRequestTimeout(50000)
                .setMaxConnections(1000)
                .setMaxConnectionsPerHost(200)
                .setPooledConnectionIdleTimeout(-1)
                .build();
            AsyncHttpClient httpClient = new DefaultAsyncHttpClient(config);

            final CountDownLatch latch = new CountDownLatch(1);
            AsyncHandler<org.asynchttpclient.Response> handler = new AsyncHandler<org.asynchttpclient.Response>() {
                FileOutputStream outs;
                boolean firstRecvBinary = true;
                long startTime = System.currentTimeMillis();
                int httpCode = 200;

                @Override
                public State onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
                    logger.info("onStatusReceived status {}", httpResponseStatus);
                    httpCode = httpResponseStatus.getStatusCode();
                    if (httpResponseStatus.getStatusCode() != 200) {
                        logger.error("request error " +  httpResponseStatus.toString());
                    }
                    return null;
                }

                @Override
                public State onHeadersReceived(HttpHeaders httpHeaders) throws Exception {
                    outs = new FileOutputStream(new File(audioSaveFile));
                    return null;
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart) throws Exception {
                    // TODO 重要提示：此处一旦接收到数据流，即可向用户播放或者用于其他处理，以提升响应速度
                    // TODO 重要提示：请不要在此回调接口中执行耗时操作，可以以异步或者队列形式将二进制TTS语音流推送到另一线程中
                    logger.info("onBodyPartReceived " + httpResponseBodyPart.getBodyPartBytes().toString());
                    if(httpCode != 200) {
                        System.err.write(httpResponseBodyPart.getBodyPartBytes());
                    }

                    if (firstRecvBinary) {
                        firstRecvBinary = false;
                        // TODO 统计第一包数据的接收延迟，实际上接收到第一包数据后就可以进行业务处理了，比如播放或者发送给调用方，注意：这里的首包延迟也包括了网络建立链接的时间
                        logger.info("tts first latency " + (System.currentTimeMillis() - startTime) + " ms");
                    }
                    // TODO 重要提示：此处仅为举例，将语音流保存到文件中
                    outs.write(httpResponseBodyPart.getBodyPartBytes());
                    return null;
                }

                @Override
                public void onThrowable(Throwable throwable) {
                    logger.error("throwable {}", throwable);
                    latch.countDown();
                }

                @Override
                public org.asynchttpclient.Response onCompleted() throws Exception {
                    logger.info("completed");
                    logger.info("tts total latency " + (System.currentTimeMillis() - startTime) + " ms");
                    outs.close();
                    latch.countDown();
                    return null;
                }
            };

            httpClient.prepareGet(url).execute(handler);
            // 等待合成完成
            latch.await();
            httpClient.close();
        }catch (Exception e) {
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("SpeechSynthesizerRestfulDemo need params: <token> <app-key>");
            System.exit(-1);
        }
        String token = args[0];
        String appkey = args[1];

        SpeechSynthesizerRestfulChunkedDemo demo = new SpeechSynthesizerRestfulChunkedDemo(appkey, token);
        String text = "我家的后面有一个很大的园，相传叫作百草园。现在是早已并屋子一起卖给朱文公的子孙了，连那最末次的相见也已经隔了七八年，其中似乎确凿只有一些野草；但那时却是我的乐园。";

        text = "你好";
        // 采用RFC 3986规范进行urlencode编码
        String textUrlEncode = text;
        try {
            textUrlEncode = URLEncoder.encode(textUrlEncode, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        System.out.println(textUrlEncode);

        String audioSaveFile = "tts4RestfulChunked.wav";
        String format = "wav";
        int sampleRate = 16000;
        // TODO 说明 最后一个参数为true表示使用http chunked机制
        demo.processGETRequet(textUrlEncode, audioSaveFile, format, sampleRate, "aixia", true);
        System.out.println("### Game Over ###");
    }
}