package com.alibaba.nls.client;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 此示例演示了：
 *      长文本语音合成API调用（setLongText）。
 *      流式合成TTS。
 *      首包延迟计算。
 *
 * 说明：该示例和nls-example-tts下的SpeechSynthesizerLongTextDemo不完全相同，长文本语音合成是单独的产品功能，是将一长串文本直接发送给服务端去合成，
 * 而SpeechSynthesizerLongTextDemo演示的是将一长串文本在调用方处切割然后分段调用语音合成接口。
 */
public class SpeechLongSynthesizerDemoTest {
    private static final Logger logger = LoggerFactory.getLogger(SpeechLongSynthesizerDemoTest.class);
    private static long startTime;
    private String appKey;
    NlsClient client;
//    public SpeechLongSynthesizerDemoTest(String appKey, String token, String url) {
//        this.appKey = appKey;
//        //创建NlsClient实例应用全局创建一个即可。生命周期可和整个应用保持一致，默认服务地址为阿里云线上服务地址。
//        if(url.isEmpty()) {
//            client = new NlsClient(token);
//        } else {
//            client = new NlsClient(url, token);
//        }
//    }

    public SpeechLongSynthesizerDemoTest(String appKey, String accessKeyId, String accessKeySecret) {
        this.appKey = appKey;
        //应用全局创建一个NlsClient实例，默认服务地址为阿里云线上服务地址。
        //获取token，使用时注意在accessToken.getExpireTime()过期前再次获取。
        AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
        try {
            accessToken.apply();
            System.out.println("get token: " + accessToken.getToken() + ", expire time: " + accessToken.getExpireTime());
            client = new NlsClient(accessToken.getToken());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static SpeechSynthesizerListener getSynthesizerListener(String musicOutFile) {
        SpeechSynthesizerListener listener = null;
        try {
            listener = new SpeechSynthesizerListener() {
                File f=new File("musicOutFile");
                FileOutputStream fout = new FileOutputStream(f);
                private boolean firstRecvBinary = true;
                //语音合成结束
                @Override
                public void onComplete(SpeechSynthesizerResponse response) {
                    // 调用onComplete时，表示所有TTS数据已经接收完成，因此为整个合成数据的延迟。该延迟可能较大，不一定满足实时场景。
                    System.out.println("name: " + response.getName() + ", status: " + response.getStatus()+", output file :"+f.getAbsolutePath());
                }
                //语音合成的语音二进制数据
                @Override
                public void onMessage(ByteBuffer message) {
                    try {
                        if(firstRecvBinary) {
                            // 此处计算首包语音流的延迟，收到第一包语音流时，即可以进行语音播放，以提升响应速度（特别是实时交互场景下）。
                            firstRecvBinary = false;
                            long now = System.currentTimeMillis();
                            logger.info("tts first latency : " + (now - SpeechLongSynthesizerDemoTest.startTime) + " ms");
                        }
                        byte[] bytesArray = new byte[message.remaining()];
                        message.get(bytesArray, 0, bytesArray.length);
                        //System.out.println("write array:" + bytesArray.length);
                        fout.write(bytesArray);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onFail(SpeechSynthesizerResponse response){
                    // task_id是调用方和服务端通信的唯一标识，当遇到问题时，需要提供此task_id以便排查。
                    System.out.println(
                            "task_id: " + response.getTaskId() +
                                    //状态码
                                    ", status: " + response.getStatus() +
                                    //错误信息
                                    ", status_text: " + response.getStatusText());
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listener;
    }
    public void process(String text,String musicOutFile) {
        SpeechSynthesizer synthesizer = null;
        try {
            //创建实例，建立连接。
            synthesizer = new SpeechSynthesizer(client, getSynthesizerListener(musicOutFile));
            synthesizer.setAppKey(appKey);
            //设置返回音频的编码格式。
            synthesizer.setFormat(OutputFormatEnum.MP3);
            //设置返回音频的采样率。
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            //发音人。注意Java SDK不支持调用超高清场景对应的发音人（例如"zhiqi"），如需调用请使用restfulAPI方式。
            synthesizer.setVoice("siyue");
            //语调，范围是-500~500，可选，默认是0。
            synthesizer.setPitchRate(0);
            //语速，范围是-500~500，默认是0。
//            synthesizer.setSpeechRate(0);
            //设置用于语音合成的文本
            // 此处调用的是setLongText接口（原语音合成接口是setText）。
            synthesizer.setLongText(text);
            //此方法将以上参数设置序列化为JSON发送给服务端，并等待服务端确认。
            long start = System.currentTimeMillis();
            synthesizer.start();
            logger.info("tts start latency " + (System.currentTimeMillis() - start) + " ms");
            SpeechLongSynthesizerDemoTest.startTime = System.currentTimeMillis();
            //等待语音合成结束
            synthesizer.waitForComplete();
            logger.info("tts stop latency " + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //关闭连接
            if (null != synthesizer) {
                synthesizer.close();
            }
        }
    }
    public void shutdown() {
        client.shutdown();
    }
    public static void main(String[] args) throws Exception {
        String appKey = "";
        String id = "";
        String secret = "";


//        String ttsTextLong = "百草堂与三味书屋 鲁迅 \n" +
//                "我家的后面有一个很大的园，相传叫作百草园。现在是早已并屋子一起卖给朱文公的子孙了，连那最末次的相见也已经隔了七八年，其中似乎确凿只有一些野草；但那时却是我的乐园。\n" +
//                "不必说碧绿的菜畦，光滑的石井栏，高大的皂荚树，紫红的桑葚；也不必说鸣蝉在树叶里长吟，肥胖的黄蜂伏在菜花上，轻捷的叫天子(云雀)忽然从草间直窜向云霄里去了。\n" +
//                "单是周围的短短的泥墙根一带，就有无限趣味。油蛉在这里低唱，蟋蟀们在这里弹琴。翻开断砖来，有时会遇见蜈蚣；还有斑蝥，倘若用手指按住它的脊梁，便会啪的一声，\n" +
//                "从后窍喷出一阵烟雾。何首乌藤和木莲藤缠络着，木莲有莲房一般的果实，何首乌有臃肿的根。有人说，何首乌根是有像人形的，吃了便可以成仙，我于是常常拔它起来，牵连不断地拔起来，\n" +
//                "也曾因此弄坏了泥墙，却从来没有见过有一块根像人样! 如果不怕刺，还可以摘到覆盆子，像小珊瑚珠攒成的小球，又酸又甜，色味都比桑葚要好得远......";

        String fileOutput = "/Users/shixiaoqi/Downloads/7月13日.xml";
        String musicOutFile = "/Users/shixiaoqi/Downloads/666.mp3";
//        String ttsTextLong = new String(Files.readAllBytes(Paths.get(fileOutput)));
        String ttsTextLong = "<speak>请闭上眼睛休息一下<break time=\"500ms\"/>好了，请睁开眼睛。</speak>\n<speak>请闭上眼睛休息一下<break time=\"500ms\"/>好了，请睁开眼睛。</speak>";

        System.out.println("ttsTextLong===>"+ttsTextLong);

        SpeechLongSynthesizerDemoTest demo = new SpeechLongSynthesizerDemoTest(appKey, id, secret);
        demo.process(ttsTextLong,musicOutFile);
        demo.shutdown();
    }
}