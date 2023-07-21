package com.alibaba.nls.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
 *      3. 长文本的分段合成及拼接
 */
public class SpeechSynthesizerRestfulChunkedWithLongTextDemo {
    private static Logger logger = LoggerFactory.getLogger(SpeechSynthesizerRestfulChunkedWithLongTextDemo.class);

    private String accessToken;
    private String appkey;
    int totalSize = 0;

    public SpeechSynthesizerRestfulChunkedWithLongTextDemo(String appkey, String token) {
        this.appkey = appkey;
        this.accessToken = token;
    }

    public void processGETRequet(String text, final FileOutputStream fout, String format, int sampleRate, String voice, boolean chunked) {
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
                .setConnectTimeout(5000)
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
                    return null;
                }

                @Override
                public State onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart) throws Exception {
                    // TODO 重要提示：此处一旦接收到数据流，即可向用户播放或者用于其他处理，以提升响应速度
                    // TODO 重要提示：请不要在此回调接口中执行耗时操作，可以以异步或者队列形式将二进制TTS语音流推送到另一线程中
                    if(httpCode != 200) {
                        System.err.write(httpResponseBodyPart.getBodyPartBytes());
                    }

                    if (firstRecvBinary) {
                        firstRecvBinary = false;
                        // TODO 统计第一包数据的接收延迟，实际上接收到第一包数据后就可以进行业务处理了，比如播放或者发送给调用方，注意：这里的首包延迟也包括了网络建立链接的时间
                        logger.info("tts first latency " + (System.currentTimeMillis() - startTime) + " ms");
                    }
                    // TODO 重要提示：此处仅为举例，将语音流保存到文件中
                    fout.write(httpResponseBodyPart.getBodyPartBytes());
                    totalSize += httpResponseBodyPart.getBodyPartBytes().length;
                    return null;
                }

                @Override
                public void onThrowable(Throwable throwable) {
                    logger.error("throwable {}", throwable);
                    latch.countDown();
                }

                @Override
                public org.asynchttpclient.Response onCompleted() throws Exception {
                    logger.info("tts total latency " + (System.currentTimeMillis() - startTime) + " ms");
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

    /**
     * 将长文本切分为每句字数不大于size数目的短句
     * @param text
     * @param size
     * @return
     */
    public static List<String> splitLongText(String text, int size) {
        //先按标点符号切分
        String[] texts = text.split("[、，。；？！,!\\?]");
        StringBuilder textPart = new StringBuilder();
        List<String> result = new ArrayList<String>();
        int len = 0;
        //再按size merge,避免标点符号切分出来的太短
        for (int i = 0; i < texts.length; i++) {
            if (textPart.length() + texts[i].length() + 1 > size) {
                result.add(textPart.toString());
                textPart.delete(0, textPart.length());

            }
            textPart.append(texts[i]);
            len += texts[i].length();
            if(len<text.length()){
                //System.out.println("at " + text.charAt(len));
                textPart.append(text.charAt(len));
                len += 1;
            }

        }
        if (textPart.length() > 0) {
            result.add(textPart.toString());
        }

        return result;

    }

    public void process(final String longText, final FileOutputStream fout) {
        List<String> textArr = splitLongText(longText, 100);
        try {
            for (int i = 0; i < textArr.size(); i++) {
                //设置用于语音合成的文本
                String text = textArr.get(i);
                System.out.println("try process [" + text + "]");
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
                // TODO 说明 最后一个参数为true表示使用http chunked机制
                processGETRequet(textUrlEncode, fout, "mp3", 16000, "siyue", true);
                System.out.println("==== " + totalSize);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        String AccessKeyID = "";
        String AccessKeySecret = "";
        String token = AccessTokenUtils.getToken(AccessKeyID, AccessKeySecret);
        String appkey = "";

        String fileInput = "/Users/shixiaoqi/Downloads/7月13日.xml";
        String fileOutput = "/Users/shixiaoqi/Downloads/longText4TTS4Restful.mp3";

        String ttsTextLong = new String(Files.readAllBytes(Paths.get(fileInput)));



//        String ttsTextLong = "百草堂与三味书屋 鲁迅 \n" +
//            "我家的后面有一个很大的园，相传叫作百草园。现在是早已并屋子一起卖给朱文公的子孙了，连那最末次的相见也已经隔了七八年，其中似乎确凿只有一些野草；但那时却是我的乐园。\n" +
//            "不必说碧绿的菜畦，光滑的石井栏，高大的皂荚树，紫红的桑葚；也不必说鸣蝉在树叶里长吟，肥胖的黄蜂伏在菜花上，轻捷的叫天子(云雀)忽然从草间直窜向云霄里去了。\n" +
//            "单是周围的短短的泥墙根一带，就有无限趣味。油蛉在这里低唱，蟋蟀们在这里弹琴。翻开断砖来，有时会遇见蜈蚣；还有斑蝥，倘若用手指按住它的脊梁，便会啪的一声，\n" +
//            "从后窍喷出一阵烟雾。何首乌藤和木莲藤缠络着，木莲有莲房一般的果实，何首乌有臃肿的根。有人说，何首乌根是有像人形的，吃了便可以成仙，我于是常常拔它起来，牵连不断地拔起来，\n" +
//            "也曾因此弄坏了泥墙，却从来没有见过有一块根像人样! 如果不怕刺，还可以摘到覆盆子，像小珊瑚珠攒成的小球，又酸又甜，色味都比桑葚要好得远......";

        try {
            String path = fileOutput;
            File out = new File(path);
            FileOutputStream fout = new FileOutputStream(out);

            // 初期并不知道wav文件实际长度，假设为0，最后再校正
//            int pcmSize = 0;
//            WavHeader header = new WavHeader();
//            // 长度字段 = 内容的大小（PCMSize) + 头部字段的大小(不包括前面4字节的标识符RIFF以及fileLength本身的4字节)
//            header.fileLength = pcmSize + (44 - 8);
//            header.fmtHdrLeth = 16;
//            header.bitsPerSample = 16;
//            header.channels = 1;
//            header.formatTag = 0x0001;
//            header.samplesPerSec = 16000;
//            header.blockAlign = (short) (header.channels * header.bitsPerSample / 8);
//            header.avgBytesPerSec = header.blockAlign * header.samplesPerSec;
//            header.dataHdrLeth = pcmSize;
//            byte[] h = header.getHeader();
//            assert h.length == 44;
//            // TODO 说明： 先写入44字节的wav头，如果合成的不是wav，比如是pcm，则不需要此步骤
//            fout.write(h);

            SpeechSynthesizerRestfulChunkedWithLongTextDemo demo = new SpeechSynthesizerRestfulChunkedWithLongTextDemo(appkey, token);
            demo.process(ttsTextLong, fout);

//            // TODO 说明： 更新44字节的wav头，如果合成的不是wav，比如是pcm，则不需要此步骤
//            RandomAccessFile wavFile = new RandomAccessFile(path, "rw");
//            int fileLength = (int)wavFile.length();
//            int dataSize = fileLength - 44;
//            System.out.println("filelength = " + fileLength +", datasize = " + dataSize);
//            header.fileLength = fileLength - 8;
//            header.dataHdrLeth = fileLength - 44;
//            wavFile.write(header.getHeader());
//            wavFile.close();

            System.out.println("### Game Over ###");
        }catch (IOException e) {

        }
    }
}